"""HTTPS/RSA-SHA256 filter updater compatible with Guardian signed manifests."""
import base64,hashlib,json,os,re,tempfile,urllib.error,urllib.request
from pathlib import Path
from urllib.parse import urlsplit
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding,rsa
from dns_engine import DomainRules
class FilterUpdateError(RuntimeError): pass
class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl): return None
def fetch_https(url,max_bytes):
    parsed=urlsplit(url)
    if parsed.scheme!="https" or not parsed.hostname or parsed.username or parsed.password: raise FilterUpdateError("Filter downloads require HTTPS without embedded credentials")
    opener=urllib.request.build_opener(_NoRedirect)
    request=urllib.request.Request(url,headers={"Accept":"text/plain","User-Agent":"GuardianWindowsFilterUpdater/1.0"})
    try:
        with opener.open(request,timeout=30) as response:
            if response.status!=200: raise FilterUpdateError(f"Unexpected HTTP status {response.status}")
            body=response.read(max_bytes+1)
            if len(body)>max_bytes: raise FilterUpdateError("Downloaded file exceeds its size limit")
            return body,dict(response.headers.items())
    except urllib.error.HTTPError as exc: raise FilterUpdateError(f"HTTPS request failed: HTTP {exc.code}") from exc
    except (urllib.error.URLError,TimeoutError,OSError) as exc: raise FilterUpdateError(f"HTTPS request failed: {exc}") from exc

def _atomic_write(path:Path,data:bytes):
    path.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix=path.name+".",suffix=".tmp",dir=path.parent)
    try:
        with os.fdopen(fd,"wb") as stream: stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(tmp,path)
    finally:
        if os.path.exists(tmp): os.unlink(tmp)

class FilterUpdater:
    def __init__(self,base_url,source_id,public_key_b64,state_path:Path,rules_path:Path,fetcher=fetch_https):
        self.base_url=base_url; self.source_id=source_id; self.public_key_b64=public_key_b64
        self.state_path=Path(state_path); self.rules_path=Path(rules_path); self.fetcher=fetcher
    def _parse_manifest(self,body):
        try: text=body.decode("utf-8","strict")
        except UnicodeDecodeError as exc: raise FilterUpdateError("Manifest is not valid UTF-8") from exc
        values={}
        for line in text.splitlines():
            if not line or "=" not in line: raise FilterUpdateError("Manifest contains an invalid line")
            key,value=line.split("=",1)
            if key in values: raise FilterUpdateError(f"Duplicate manifest field: {key}")
            values[key]=value
        required={"manifestVersion","generatedAt","sourceId","sourceVersion","url","sha256","minimumRules"}
        if set(values)!=required: raise FilterUpdateError("Manifest fields do not match version 1 schema")
        if values["manifestVersion"]!="1" or values["sourceId"]!=self.source_id: raise FilterUpdateError("Unsupported manifest version or source ID mismatch")
        if not re.fullmatch(r"[A-Za-z0-9._+-]{1,64}",values["sourceVersion"]): raise FilterUpdateError("Invalid filter source version")
        if not re.fullmatch(r"[0-9a-f]{64}",values["sha256"]): raise FilterUpdateError("Invalid SHA-256 field")
        try: generated=int(values["generatedAt"]); minimum=int(values["minimumRules"])
        except ValueError as exc: raise FilterUpdateError("Invalid manifest numeric field") from exc
        if generated<=0 or minimum<1: raise FilterUpdateError("Invalid manifest timestamp or minimum rule count")
        values["generatedAt"]=generated; values["minimumRules"]=minimum
        return values
    def update(self):
        base=urlsplit(self.base_url)
        if base.scheme!="https" or not base.hostname or base.username or base.password or base.query or base.fragment or base.path not in ("","/"):
            raise FilterUpdateError("Manifest base URL must be an HTTPS origin")
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}",self.source_id): raise FilterUpdateError("Invalid filter source ID")
        try:
            key_bytes=base64.b64decode(self.public_key_b64,validate=True)
            public_key=serialization.load_der_public_key(key_bytes)
        except Exception as exc: raise FilterUpdateError("Pinned manifest public key is invalid") from exc
        if not isinstance(public_key,rsa.RSAPublicKey) or public_key.key_size<2048 or public_key.key_size>4096:
            raise FilterUpdateError("Pinned key must be RSA 2048–4096 bits")
        origin=f"{base.scheme}://{base.netloc}"
        manifest_url=f"{origin}/{self.source_id}.manifest"
        manifest,headers=self.fetcher(manifest_url,16*1024)
        signature=next((v for k,v in headers.items() if k.lower()=="x-guardian-signature"),"")
        try: signature_bytes=base64.b64decode(signature,validate=True)
        except Exception as exc: raise FilterUpdateError("Manifest signature header is missing or malformed") from exc
        try: public_key.verify(signature_bytes,manifest,padding.PKCS1v15(),hashes.SHA256())
        except Exception as exc: raise FilterUpdateError("Manifest RSA-SHA256 signature verification failed") from exc
        values=self._parse_manifest(manifest)
        filter_url=urlsplit(values["url"])
        if filter_url.scheme!="https" or filter_url.netloc!=base.netloc or filter_url.username or filter_url.password or filter_url.query or filter_url.fragment:
            raise FilterUpdateError("Filter URL must remain on the configured HTTPS origin")
        if not filter_url.path.startswith(f"/filters/{self.source_id}/") or not filter_url.path.endswith(".txt"):
            raise FilterUpdateError("Filter URL is outside the expected source directory")
        previous=0
        if self.state_path.exists():
            try: previous=int(json.loads(self.state_path.read_text(encoding="utf-8")).get("generatedAt",0))
            except (OSError,ValueError,TypeError,json.JSONDecodeError) as exc: raise FilterUpdateError("Stored anti-rollback state is corrupt") from exc
        if values["generatedAt"]<=previous: raise FilterUpdateError("Manifest is stale or has already been applied")
        filter_bytes,_=self.fetcher(values["url"],10*1024*1024)
        if hashlib.sha256(filter_bytes).hexdigest()!=values["sha256"]: raise FilterUpdateError("Filter SHA-256 does not match the signed manifest")
        try: filter_text=filter_bytes.decode("utf-8","strict")
        except UnicodeDecodeError as exc: raise FilterUpdateError("Filter is not valid UTF-8") from exc
        if not filter_bytes or b"\0" in filter_bytes: raise FilterUpdateError("Filter is empty or contains NUL bytes")
        rules=DomainRules.from_text(filter_text)
        if len(rules.blocked)<values["minimumRules"]: raise FilterUpdateError("Filter has fewer valid blocking rules than the signed minimum")
        _atomic_write(self.rules_path,filter_bytes)
        state={"sourceId":self.source_id,"sourceVersion":values["sourceVersion"],"generatedAt":values["generatedAt"],"sha256":values["sha256"]}
        _atomic_write(self.state_path,json.dumps(state,sort_keys=True).encode("utf-8"))
        return state
