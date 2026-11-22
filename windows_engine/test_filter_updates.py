import base64,hashlib
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


def test_corrupt_anti_rollback_state_fails_closed_and_preserves_rules(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    rules=tmp_path/"rules.txt"; old=b"||known-good.example^\\n"; rules.write_bytes(old)
    state=tmp_path/"state.json"; state.write_text("{broken",encoding="utf-8")
    updater=FilterUpdater("https://filters.example.com","adguard",public,state,rules,fetcher)
    with pytest.raises(FilterUpdateError,match="anti-rollback state is corrupt"):
        updater.update()
    assert rules.read_bytes()==old
    assert state.read_text(encoding="utf-8")=="{broken"


def test_filter_checksum_mismatch_does_not_replace_existing_cache(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    rules=tmp_path/"rules.txt"; old=b"||known-good.example^\\n"; rules.write_bytes(old)
    def tampered(url,limit):
        if url.endswith(".manifest"):
            return manifest,{"X-Guardian-Signature":signature}
        return b"||tampered.example^\\n",{}
    updater=FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state.json",rules,tampered)
    with pytest.raises(FilterUpdateError,match="SHA-256"):
        updater.update()
    assert rules.read_bytes()==old
    assert not (tmp_path/"state.json").exists()


def test_signed_manifest_minimum_rule_count_is_enforced(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    too_many=manifest.replace(b"minimumRules=2",b"minimumRules=3")
    signed=base64.b64encode(private.sign(too_many,padding.PKCS1v15(),hashes.SHA256())).decode("ascii")
    def underfilled(url,limit):
        if url.endswith(".manifest"):
            return too_many,{"X-Guardian-Signature":signed}
        return body,{}
    rules=tmp_path/"rules.txt"; old=b"||known-good.example^\\n"; rules.write_bytes(old)
    updater=FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state.json",rules,underfilled)
    with pytest.raises(FilterUpdateError,match="fewer valid blocking rules"):
        updater.update()
    assert rules.read_bytes()==old
    assert not (tmp_path/"state.json").exists()


def test_filter_download_failure_preserves_last_known_good_cache(tmp_path):
    public,private,body,manifest,signature,fetcher=signed_fixture()
    rules=tmp_path/"rules.txt"; old=b"||known-good.example^\\n"; rules.write_bytes(old)
    def unavailable(url,limit):
        if url.endswith(".manifest"):
            return manifest,{"X-Guardian-Signature":signature}
        raise FilterUpdateError("simulated network outage")
    updater=FilterUpdater("https://filters.example.com","adguard",public,tmp_path/"state.json",rules,unavailable)
    with pytest.raises(FilterUpdateError,match="network outage"):
        updater.update()
    assert rules.read_bytes()==old
    assert not (tmp_path/"state.json").exists()
