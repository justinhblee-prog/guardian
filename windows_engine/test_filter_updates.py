import base64,hashlib,time
import pytest
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding,rsa
from windows_engine.filter_updates import FilterUpdater,FilterUpdateError

def signed_fixture():
    private=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    public=base64.b64encode(private.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)).decode("ascii")
    body=b"||ads.example^\n||tracker.example^\n"
    manifest=(
      "manifestVersion=1\n"
      "generatedAt=1700000001\n"
      "sourceId=adguard\n"
      "sourceVersion=2026.11.16\n"
      "url=https://filters.example.com/filters/adguard/2026.11.16.txt\n"
      f"sha256={hashlib.sha256(body).hexdigest()}\n"
      "minimumRules=2\n"
    ).encode("utf-8")
    signature=base64.b64encode(private.sign(manifest,padding.PKCS1v15(),hashes.SHA256())).decode("ascii")
    def fetcher(url,limit):
        if url.endswith(".manifest"): return manifest,{"X-Guardian-Signature":signature}
        return body,{}
    return public,private,body,manifest,signature,fetcher

def test_signed_manifest_download_and_atomic_cache(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    rules=tmp_path/"rules.txt"; state=tmp_path/"manifest-state.json"
    result=FilterUpdater("https://filters.example.com","adguard",public,state,rules,fetcher).update()
    assert rules.read_bytes()==body and result["sourceVersion"]=="2026.11.16"
    assert state.exists()

def test_manifest_signature_mismatch_keeps_existing_cache(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    old=b"||old.example^\n"; rules=tmp_path/"rules.txt"; rules.write_bytes(old)
    def broken(url,limit):
        content,headers=fetcher(url,limit)
        if url.endswith(".manifest"): return content+b" ",headers
        return content,headers
    updater=FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state.json",rules,broken)
    with pytest.raises(FilterUpdateError): updater.update()
    assert rules.read_bytes()==old

def test_anti_rollback_rejects_same_manifest(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    state=tmp_path/"state.json"; rules=tmp_path/"rules.txt"
    updater=FilterUpdater("https://filters.example.com","adguard",public,state,rules,fetcher)
    updater.update()
    with pytest.raises(FilterUpdateError): updater.update()

def test_filter_url_must_stay_on_pinned_origin(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    bad=manifest.replace(b"https://filters.example.com/",b"https://evil.example/")
    # Sign the malicious URL with the trusted key: the origin allowlist must reject it.
    bad_signature=base64.b64encode(private.sign(bad,padding.PKCS1v15(),hashes.SHA256())).decode("ascii")
    def bad_fetch(url,limit):
        if url.endswith(".manifest"): return bad,{"X-Guardian-Signature":bad_signature}
        return body,{}
    with pytest.raises(FilterUpdateError): FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state",tmp_path/"rules",bad_fetch).update()


def test_manifest_rejects_timestamp_more_than_ten_minutes_in_future(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    future=int(time.time())+601
    forged_time=manifest.replace(b"generatedAt=1700000001",f"generatedAt={future}".encode("ascii"))
    future_signature=base64.b64encode(private.sign(forged_time,padding.PKCS1v15(),hashes.SHA256())).decode("ascii")
    def future_fetch(url,limit):
        if url.endswith(".manifest"): return forged_time,{"X-Guardian-Signature":future_signature}
        return body,{}
    with pytest.raises(FilterUpdateError,match="too far in the future"):
        FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state.json",tmp_path/"rules.txt",future_fetch).update()
    assert not (tmp_path/"rules.txt").exists()


@pytest.mark.parametrize("value",["+1700000001","1700000001_0"," 1700000001"])
def test_manifest_rejects_noncanonical_generated_at(value,tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    malformed=manifest.replace(b"generatedAt=1700000001",f"generatedAt={value}".encode("ascii"))
    malformed_signature=base64.b64encode(private.sign(malformed,padding.PKCS1v15(),hashes.SHA256())).decode("ascii")
    def malformed_fetch(url,limit):
        if url.endswith(".manifest"): return malformed,{"X-Guardian-Signature":malformed_signature}
        return body,{}
    with pytest.raises(FilterUpdateError,match="Invalid manifest timestamp"):
        FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state.json",tmp_path/"rules.txt",malformed_fetch).update()
