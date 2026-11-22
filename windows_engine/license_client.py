"""Shared Windows/Android Guardian Pro license client.

The server signing private key never leaves the API host. Windows embeds only the
X.509 RSA public key and verifies the exact UTF-8 token bytes returned by HTTPS.
"""
import base64
import hashlib
import json
import os
import re
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


class LicenseClientError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code

    @property
    def invalidates_entitlement(self) -> bool:
        return self.status_code == 403


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_api_base_url(value: str) -> str:
    parsed = urlsplit((value or "").strip())
    if (parsed.scheme.lower() != "https" or not parsed.hostname or
            parsed.username or parsed.password or parsed.query or parsed.fragment or
            parsed.path not in ("", "/")):
        raise LicenseClientError("License API must be an HTTPS origin without credentials, path, query or fragment")
    return f"https://{parsed.netloc}"


def verify_entitlement(payload: str, signature_b64: str, public_key_b64: str,
                       expected_device_binding: str, now: int | None = None) -> dict:
    if not isinstance(payload, str) or not payload or len(payload.encode("utf-8")) > 4096:
        raise LicenseClientError("Entitlement payload is empty or too large")
    if not re.fullmatch(r"[a-fA-F0-9]{64}", expected_device_binding or ""):
        raise LicenseClientError("Device binding is invalid")
    values = {}
    for line in payload.splitlines():
        if not line or "=" not in line:
            raise LicenseClientError("Entitlement contains an invalid line")
        key, value = line.split("=", 1)
        if key in values or not value or key not in {
            "tokenVersion", "product", "entitlement", "issuedAt", "expiresAt",
            "licenseRef", "deviceBinding"
        }:
            raise LicenseClientError("Entitlement contains duplicate, unknown or empty claims")
        values[key] = value
    required = {"tokenVersion", "product", "entitlement", "issuedAt", "expiresAt", "licenseRef", "deviceBinding"}
    if set(values) != required:
        raise LicenseClientError("Entitlement is missing required claims")
    if values["tokenVersion"] != "1" or values["product"] not in ("guardian", "guardian-android") or values["entitlement"] != "pro":
        raise LicenseClientError("Entitlement is not a supported Guardian Pro license")
    if not re.fullmatch(r"[a-zA-Z0-9._-]{8,128}", values["licenseRef"]):
        raise LicenseClientError("License reference is invalid")
    if not re.fullmatch(r"[a-fA-F0-9]{64}", values["deviceBinding"]) or values["deviceBinding"].lower() != expected_device_binding.lower():
        raise LicenseClientError("Entitlement belongs to another device")
    try:
        issued = int(values["issuedAt"])
        expires = int(values["expiresAt"])
        key_bytes = base64.b64decode(public_key_b64, validate=True)
        signature = base64.b64decode(signature_b64, validate=True)
        public_key = serialization.load_der_public_key(key_bytes)
    except Exception as exc:
        raise LicenseClientError("Entitlement key, signature or time encoding is invalid") from exc
    if not isinstance(public_key, rsa.RSAPublicKey) or public_key.key_size < 2048:
        raise LicenseClientError("License public key must be RSA 2048-bit or stronger")
    try:
        public_key.verify(signature, payload.encode("utf-8"), padding.PKCS1v15(), hashes.SHA256())
    except Exception as exc:
        raise LicenseClientError("Entitlement signature verification failed") from exc
    current = int(time.time()) if now is None else int(now)
    if issued <= 0 or expires <= issued or issued > current + 300 or expires <= current:
        raise LicenseClientError("Entitlement is not currently valid")
    return {
        "product": values["product"], "entitlement": values["entitlement"],
        "issuedAt": issued, "expiresAt": expires, "licenseRef": values["licenseRef"],
        "deviceBinding": values["deviceBinding"].lower()
    }


class WindowsLicenseClient:
    def __init__(self, api_base_url: str, public_key_b64: str, data_dir: Path,
                 app_version: str = "1.0.0", opener=None):
        self.api_base_url = validate_api_base_url(api_base_url)
        self.public_key_b64 = public_key_b64.strip()
        self.data_dir = Path(data_dir)
        self.state_path = self.data_dir / "license-entitlement.json"
        self.device_path = self.data_dir / "license-device-id"
        self.app_version = app_version[:40]
        self.opener = opener or urllib.request.build_opener(_NoRedirect)

    def device_binding(self) -> str:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        if self.device_path.exists():
            value = self.device_path.read_text(encoding="ascii").strip()
            if re.fullmatch(r"[a-f0-9]{64}", value):
                return value
            raise LicenseClientError("Stored device identity is malformed; refusing to silently consume another activation slot")
        binding = hashlib.sha256(os.urandom(32)).hexdigest()
        fd, temporary = tempfile.mkstemp(prefix="guardian-device-", dir=self.data_dir)
        try:
            with os.fdopen(fd, "w", encoding="ascii") as stream:
                stream.write(binding + "\n")
                stream.flush()
                os.fsync(stream.fileno())
            try:
                os.link(temporary, self.device_path)
            except FileExistsError:
                return self.device_binding()
            return binding
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)

    def _post(self, route: str, body: dict) -> dict:
        request = urllib.request.Request(
            self.api_base_url + route,
            data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
            headers={"Accept": "application/json", "Content-Type": "application/json", "Cache-Control": "no-store"},
            method="POST"
        )
        try:
            with self.opener.open(request, timeout=10) as response:
                if response.status != 200:
                    raise LicenseClientError(f"License API returned HTTP {response.status}")
                raw = response.read(8193)
        except urllib.error.HTTPError as exc:
            raise LicenseClientError(
                f"License API rejected the request (HTTP {exc.code})",
                status_code=exc.code
            ) from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise LicenseClientError(f"License API connection failed: {exc}") from exc
        if len(raw) > 8192:
            raise LicenseClientError("License API response exceeds its size limit")
        try:
            result = json.loads(raw.decode("utf-8", "strict"))
        except Exception as exc:
            raise LicenseClientError("License API response is not valid UTF-8 JSON") from exc
        if not isinstance(result, dict):
            raise LicenseClientError("License API response must be a JSON object")
        return result

    def _accept_response(self, response: dict) -> dict:
        payload, signature = response.get("payload"), response.get("signature")
        if not isinstance(payload, str) or not isinstance(signature, str):
            raise LicenseClientError("License API did not return a signed entitlement")
        entitlement = verify_entitlement(payload, signature, self.public_key_b64, self.device_binding())
        self.data_dir.mkdir(parents=True, exist_ok=True)
        record = {"payload": payload, "signature": signature, "licenseRef": entitlement["licenseRef"]}
        temporary = self.state_path.with_suffix(".tmp")
        temporary.write_text(json.dumps(record, separators=(",", ":")), encoding="utf-8")
        os.replace(temporary, self.state_path)
        return entitlement

    def activate(self, license_key: str) -> dict:
        key = (license_key or "").strip().upper()
        if not re.fullmatch(r"GDN-(?:[A-F0-9]{8}-){5}[A-F0-9]{8}", key):
            raise LicenseClientError("License key format is invalid")
        return self._accept_response(self._post("/v1/licenses/activate", {
            "licenseKey": key, "deviceBinding": self.device_binding(), "appVersion": self.app_version
        }))

    def refresh(self) -> dict:
        try:
            record = json.loads(self.state_path.read_text(encoding="utf-8"))
            license_ref = record["licenseRef"]
        except Exception as exc:
            raise LicenseClientError("No saved Guardian license is available to refresh") from exc
        try:
            response = self._post("/v1/licenses/refresh", {
                "deviceBinding": self.device_binding(), "licenseRef": license_ref
            })
        except LicenseClientError as exc:
            if exc.invalidates_entitlement:
                self.state_path.unlink(missing_ok=True)
            raise
        return self._accept_response(response)

    def status(self) -> dict:
        """Check the server-side activation; a free response revokes local Pro access."""
        try:
            record = json.loads(self.state_path.read_text(encoding="utf-8"))
            license_ref = record["licenseRef"]
        except FileNotFoundError:
            return {"status": "free"}
        except Exception as exc:
            raise LicenseClientError("Saved Guardian license state is malformed") from exc
        try:
            response = self._post("/v1/licenses/status", {
                "deviceBinding": self.device_binding(), "licenseRef": license_ref
            })
        except LicenseClientError as exc:
            if exc.invalidates_entitlement:
                self.state_path.unlink(missing_ok=True)
            raise
        if str(response.get("status", "")).lower() != "active":
            self.state_path.unlink(missing_ok=True)
        return response

    def deactivate(self) -> bool:
        try:
            record = json.loads(self.state_path.read_text(encoding="utf-8"))
            license_ref = record["licenseRef"]
        except FileNotFoundError:
            return False
        self._post("/v1/licenses/deactivate", {
            "deviceBinding": self.device_binding(), "licenseRef": license_ref
        })
        self.state_path.unlink(missing_ok=True)
        return True

    def current_entitlement(self, now: int | None = None) -> dict | None:
        try:
            record = json.loads(self.state_path.read_text(encoding="utf-8"))
        except (FileNotFoundError, json.JSONDecodeError):
            return None
        try:
            return verify_entitlement(record["payload"], record["signature"], self.public_key_b64,
                                      self.device_binding(), now=now)
        except (KeyError, LicenseClientError):
            return None
