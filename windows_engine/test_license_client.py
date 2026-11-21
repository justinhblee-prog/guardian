import base64
import json
import time
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from windows_engine.license_client import (
    LicenseClientError, WindowsLicenseClient, validate_api_base_url, verify_entitlement
)


@pytest.fixture
def signing_pair():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public = base64.b64encode(private.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )).decode("ascii")
    return private, public


def signed_token(private, device, product="guardian", issued=None, expires=None):
    now = int(time.time())
    payload = "\n".join([
        "tokenVersion=1", f"product={product}", "entitlement=pro",
        f"issuedAt={issued or now - 5}", f"expiresAt={expires or now + 3600}",
        "licenseRef=12345678-abcd", f"deviceBinding={device}"
    ])
    signature = base64.b64encode(private.sign(payload.encode(), padding.PKCS1v15(), hashes.SHA256())).decode()
    return payload, signature


def test_api_base_requires_https_origin():
    assert validate_api_base_url("https://license.example.com/") == "https://license.example.com"
    for value in ["http://license.example.com", "https://user:pass@license.example.com",
                  "https://license.example.com/path", "https://license.example.com?x=1"]:
        with pytest.raises(LicenseClientError):
            validate_api_base_url(value)


def test_windows_verifies_shared_signed_guardian_entitlement(signing_pair):
    private, public = signing_pair
    device = "a" * 64
    payload, signature = signed_token(private, device)
    result = verify_entitlement(payload, signature, public, device)
    assert result["product"] == "guardian"
    assert result["entitlement"] == "pro"


def test_windows_keeps_legacy_android_token_compatibility(signing_pair):
    private, public = signing_pair
    device = "b" * 64
    payload, signature = signed_token(private, device, product="guardian-android")
    assert verify_entitlement(payload, signature, public, device)["product"] == "guardian-android"


def test_windows_rejects_tampering_wrong_device_and_expired_token(signing_pair):
    private, public = signing_pair
    device = "c" * 64
    payload, signature = signed_token(private, device)
    with pytest.raises(LicenseClientError):
        verify_entitlement(payload.replace("entitlement=pro", "entitlement=free"), signature, public, device)
    with pytest.raises(LicenseClientError):
        verify_entitlement(payload, signature, public, "d" * 64)
    expired, expired_signature = signed_token(private, device, expires=int(time.time()) - 1)
    with pytest.raises(LicenseClientError):
        verify_entitlement(expired, expired_signature, public, device)


def test_device_binding_is_stable_and_saved_entitlement_is_verified(tmp_path, signing_pair):
    private, public = signing_pair
    client = WindowsLicenseClient("https://license.example.com", public, tmp_path)
    first = client.device_binding()
    assert first == client.device_binding()
    payload, signature = signed_token(private, first)
    client.state_path.parent.mkdir(parents=True, exist_ok=True)
    client.state_path.write_text(json.dumps({
        "payload": payload, "signature": signature, "licenseRef": "12345678-abcd"
    }), encoding="utf-8")
    assert client.current_entitlement()["licenseRef"] == "12345678-abcd"
    assert client.current_entitlement(now=int(time.time()) + 7200) is None


def test_malformed_saved_device_identity_is_not_replaced(tmp_path, signing_pair):
    _, public = signing_pair
    client = WindowsLicenseClient("https://license.example.com", public, tmp_path)
    client.data_dir.mkdir(parents=True)
    client.device_path.write_text("not-a-valid-device-id", encoding="ascii")
    with pytest.raises(LicenseClientError):
        client.device_binding()
    assert client.device_path.read_text(encoding="ascii") == "not-a-valid-device-id"
