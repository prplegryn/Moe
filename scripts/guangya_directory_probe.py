#!/usr/bin/env python3
import argparse
import hashlib
import json
import secrets
import time
import uuid
from pathlib import Path
from typing import Any

import requests


CLIENT_ID = "aMe-8VSlkrbQXpUR"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
)
ACCOUNT_BASE = "https://account.guangyapan.com"
API_BASE = "https://api.guangyapan.com"


def generate_did() -> str:
    raw = f"{uuid.uuid4()}{secrets.token_hex(8)}".encode()
    return hashlib.md5(raw).hexdigest()


def generate_traceparent() -> str:
    return f"00-{secrets.token_hex(16)}-{secrets.token_hex(8)}-01"


def normalize_phone(value: str) -> tuple[str, str, str]:
    compact = "".join(ch for ch in value.strip() if ch.isdigit() or ch == "+")
    digits = "".join(ch for ch in compact if ch.isdigit())
    if compact.startswith("+86") and digits.startswith("86") and len(digits) >= 13:
        national = digits[2:]
    elif not compact.startswith("+") and digits.startswith("86") and len(digits) == 13:
        national = digits[2:]
    elif len(digits) == 11:
        national = digits
    else:
        national = digits
    if len(national) == 11:
        international = f"+86 {national}"
        return national, international, international
    fallback = compact or value.strip()
    return value.strip(), fallback, fallback


class GuangyaProbe:
    def __init__(self, device_id: str | None = None) -> None:
        self.device_id = device_id or generate_did()
        self.access_token = ""
        self.refresh_token = None
        self.expires_at = None
        self.session = requests.Session()
        self.session.headers.update(
            {
                "accept": "application/json, text/plain, */*",
                "content-type": "application/json",
                "did": self.device_id,
                "dt": "4",
                "origin": "https://www.guangyapan.com",
                "referer": "https://www.guangyapan.com/",
                "user-agent": USER_AGENT,
            }
        )

    def account_headers(self, extra: dict[str, str] | None = None) -> dict[str, str]:
        headers = {
            "accept": "*/*",
            "content-type": "application/json",
            "origin": "https://www.guangyapan.com",
            "referer": "https://www.guangyapan.com/",
            "user-agent": USER_AGENT,
            "x-client-id": CLIENT_ID,
            "x-client-version": "0.0.1",
            "x-device-id": self.device_id,
            "x-device-model": "chrome%2F147.0.0.0",
            "x-device-name": "PC-Chrome",
            "x-device-sign": f"wdi10.{self.device_id}{secrets.token_hex(16)}",
            "x-net-work-type": "NONE",
            "x-os-version": "MacIntel",
            "x-platform-version": "1",
            "x-protocol-version": "301",
            "x-provider-name": "NONE",
            "x-sdk-version": "9.0.2",
        }
        if extra:
            headers.update(extra)
        return headers

    def post_json(
        self,
        url: str,
        body: dict[str, Any],
        headers: dict[str, str] | None = None,
        label: str = "request",
    ) -> dict[str, Any]:
        merged_headers = dict(headers or {})
        if url.startswith(API_BASE):
            merged_headers["authorization"] = f"Bearer {self.access_token}"
            merged_headers["traceparent"] = generate_traceparent()
        response = self.session.post(url, json=body, headers=merged_headers, timeout=40)
        text = response.text
        if not response.ok:
            raise RuntimeError(f"{label} HTTP {response.status_code}: {text[:500]}")
        if not text.strip():
            return {}
        try:
            return response.json()
        except json.JSONDecodeError as error:
            raise RuntimeError(f"{label} returned non-json: {text[:500]}") from error

    def captcha_init(self, action: str, meta: dict[str, Any], captcha_token: str | None = None) -> dict[str, Any]:
        body = {
            "client_id": CLIENT_ID,
            "action": action,
            "device_id": self.device_id,
            "meta": meta,
        }
        if captcha_token:
            body["captcha_token"] = captcha_token
        return self.post_json(
            f"{ACCOUNT_BASE}/v1/shield/captcha/init",
            body,
            headers=self.account_headers(),
            label=f"captcha init {action}",
        )

    def login_sms(self, phone: str) -> None:
        display_phone, username, verification_phone = normalize_phone(phone)
        print(f"device_id={self.device_id}")
        print(f"phone={display_phone} username={username} verification_phone={verification_phone}")

        captcha = self.captcha_init(
            "POST:/v1/auth/verification",
            {"phone_number": verification_phone},
        )
        captcha_token = deep_string(captcha, "captcha_token", "captchaToken")
        verification_url = deep_string(captcha, "url", "captcha_url", "captchaUrl")
        if verification_url:
            print(f"captcha url: {verification_url}")
        if not captcha_token:
            captcha_token = input("输入完成人机验证后的 captcha_token: ").strip()
        if not captcha_token:
            raise RuntimeError("missing captcha_token for SMS send")

        sent = self.post_json(
            f"{ACCOUNT_BASE}/v1/auth/verification",
            {
                "phone_number": verification_phone,
                "target": "ANY",
                "client_id": CLIENT_ID,
            },
            headers=self.account_headers({"x-captcha-token": captcha_token}),
            label="send sms",
        )
        verification_id = deep_string(sent, "verification_id", "verificationId")
        if not verification_id:
            raise RuntimeError(f"SMS response missing verification_id: {json.dumps(sent, ensure_ascii=False)[:500]}")
        code = input("输入短信验证码: ").strip()

        verified = self.post_json(
            f"{ACCOUNT_BASE}/v1/auth/verification/verify",
            {
                "verification_id": verification_id,
                "verification_code": code,
                "client_id": CLIENT_ID,
            },
            headers=self.account_headers(),
            label="verify sms",
        )
        verification_token = deep_string(verified, "verification_token", "verificationToken")
        if not verification_token:
            raise RuntimeError(f"verify response missing verification_token: {json.dumps(verified, ensure_ascii=False)[:500]}")

        signin_captcha = self.captcha_init(
            "POST:/v1/auth/signin",
            {"username": username},
        )
        signin_captcha_token = deep_string(signin_captcha, "captcha_token", "captchaToken")
        signin_url = deep_string(signin_captcha, "url", "captcha_url", "captchaUrl")
        if signin_url:
            print(f"signin captcha url: {signin_url}")
        if not signin_captcha_token:
            signin_captcha_token = input("输入登录人机验证后的 captcha_token: ").strip()
        if not signin_captcha_token:
            raise RuntimeError("missing captcha_token for signin")

        token = self.post_json(
            f"{ACCOUNT_BASE}/v1/auth/signin",
            {
                "verification_code": code,
                "verification_token": verification_token,
                "username": username,
                "client_id": CLIENT_ID,
            },
            headers=self.account_headers({"x-captcha-token": signin_captcha_token}),
            label="signin",
        )
        self.access_token = deep_string(token, "access_token", "accessToken") or ""
        self.refresh_token = deep_string(token, "refresh_token", "refreshToken")
        expires_in = deep_int(token, "expires_in", "expiresIn")
        self.expires_at = int(time.time() + expires_in) if expires_in else None
        if not self.access_token:
            raise RuntimeError(f"signin response missing access_token: {json.dumps(token, ensure_ascii=False)[:500]}")
        print(f"login ok: access_token={self.access_token[:18]}... refresh={'yes' if self.refresh_token else 'no'}")

    def save_auth(self, path: Path) -> None:
        data = {
            "device_id": self.device_id,
            "access_token": self.access_token,
            "refresh_token": self.refresh_token,
            "expires_at": self.expires_at,
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"auth cached at {path}")

    def load_auth(self, path: Path) -> bool:
        if not path.exists():
            return False
        data = json.loads(path.read_text(encoding="utf-8"))
        self.device_id = data.get("device_id") or self.device_id
        self.access_token = data.get("access_token") or ""
        self.refresh_token = data.get("refresh_token")
        self.expires_at = data.get("expires_at")
        if self.access_token:
            print(f"loaded cached auth: token={self.access_token[:18]}... device_id={self.device_id}")
            return True
        return False

    def list_files_raw(self, body: dict[str, Any]) -> dict[str, Any]:
        return self.post_json(
            f"{API_BASE}/userres/v1/file/get_file_list",
            body,
            label=f"list files {body}",
        )


def deep_string(value: Any, *keys: str) -> str | None:
    found = find_by_key(value, set(key.lower() for key in keys))
    if found is None:
        return None
    if isinstance(found, str):
        text = found.strip()
        return text or None
    if isinstance(found, (int, float)):
        return str(found)
    return None


def deep_int(value: Any, *keys: str) -> int | None:
    found = find_by_key(value, set(key.lower() for key in keys))
    if isinstance(found, int):
        return found
    if isinstance(found, str) and found.strip().isdigit():
        return int(found.strip())
    return None


def find_by_key(value: Any, keys: set[str]) -> Any:
    if isinstance(value, dict):
        for key, item in value.items():
            if key.lower() in keys:
                return item
        for item in value.values():
            found = find_by_key(item, keys)
            if found is not None:
                return found
    elif isinstance(value, list):
        for item in value:
            found = find_by_key(item, keys)
            if found is not None:
                return found
    return None


def find_arrays(value: Any, path: str = "$") -> list[tuple[str, list[Any]]]:
    arrays: list[tuple[str, list[Any]]] = []
    if isinstance(value, list):
        arrays.append((path, value))
        for index, item in enumerate(value[:3]):
            arrays.extend(find_arrays(item, f"{path}[{index}]"))
    elif isinstance(value, dict):
        for key, item in value.items():
            arrays.extend(find_arrays(item, f"{path}.{key}"))
    return arrays


def is_directory(item: dict[str, Any]) -> bool:
    for key in ("isDir", "is_dir", "isDirectory", "folder", "directory"):
        raw = pick(item, key)
        if isinstance(raw, bool):
            return raw
        if isinstance(raw, str):
            low = raw.strip().lower()
            if low in ("1", "true", "yes"):
                return True
            if low in ("0", "false", "no"):
                return False
    file_type = pick(item, "fileType", "file_type", "type")
    res_type = pick(item, "resType", "res_type")
    size = pick(item, "size", "fileSize", "file_size")
    return res_type in (2, "2") or file_type in (0, "0") or (file_type is None and not size)


def pick(item: dict[str, Any], *keys: str) -> Any:
    lowered = {key.lower(): value for key, value in item.items()}
    for key in keys:
        if key.lower() in lowered:
            return lowered[key.lower()]
    return None


def summarize_response(label: str, body: dict[str, Any], dump_dir: Path | None) -> tuple[str | None, int]:
    print(f"\n=== {label} ===")
    print("top keys:", list(body.keys()) if isinstance(body, dict) else type(body).__name__)
    arrays = find_arrays(body)
    if not arrays:
        print("no arrays found")
        print(json.dumps(body, ensure_ascii=False)[:1000])
        return None, 0
    best_path, best_array = max(arrays, key=lambda item: len(item[1]))
    print("arrays:", ", ".join(f"{path}[{len(array)}]" for path, array in arrays[:8]))
    print(f"best array: {best_path} count={len(best_array)}")
    dict_items = [item for item in best_array if isinstance(item, dict)]
    dirs = [item for item in dict_items if is_directory(item)]
    print(f"dict items={len(dict_items)} dirs={len(dirs)} files={len(dict_items) - len(dirs)}")
    for item in dict_items[:8]:
        file_id = pick(item, "fileId", "file_id", "id", "fid")
        name = pick(item, "fileName", "filename", "file_name", "name", "title")
        file_type = pick(item, "fileType", "file_type", "type")
        raw_dir = pick(item, "isDir", "is_dir", "isDirectory", "folder", "directory")
        parent = pick(item, "parentId", "parent_id", "pid")
        print(
            f"- dir={is_directory(item)} rawDir={raw_dir!r} type={file_type!r} "
            f"id={file_id!r} parent={parent!r} name={name!r}"
        )
    if dump_dir:
        dump_dir.mkdir(parents=True, exist_ok=True)
        safe = "".join(ch if ch.isalnum() else "_" for ch in label)[:80]
        path = dump_dir / f"{safe}.json"
        path.write_text(json.dumps(body, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"dumped {path}")
    return best_path, len(dirs)


def probe_root(client: GuangyaProbe, dump_dir: Path | None) -> None:
    variants = [
        ("root_empty_current", {"parentId": "", "page": 0, "pageSize": 100, "orderBy": 0, "sortType": 0}),
        ("root_empty_order3", {"parentId": "", "page": 0, "pageSize": 100, "orderBy": 3, "sortType": 1}),
        ("root_star_plain", {"parentId": "*", "page": 0, "pageSize": 100, "orderBy": 0, "sortType": 0}),
        ("root_star_res1", {"parentId": "*", "page": 0, "pageSize": 100, "orderBy": 3, "sortType": 1, "resType": 1}),
        ("root_omitted_parent", {"page": 0, "pageSize": 100, "orderBy": 0, "sortType": 0}),
        ("root_null_parent", {"parentId": None, "page": 0, "pageSize": 100, "orderBy": 0, "sortType": 0}),
    ]
    first_dir_id = None
    for label, body in variants:
        try:
            response = client.list_files_raw(body)
            summarize_response(label, response, dump_dir)
            if first_dir_id is None:
                arrays = find_arrays(response)
                if arrays:
                    _, best_array = max(arrays, key=lambda item: len(item[1]))
                    for item in best_array:
                        if isinstance(item, dict) and is_directory(item):
                            first_dir_id = pick(item, "fileId", "file_id", "id", "fid")
                            break
        except Exception as error:
            print(f"\n=== {label} ===")
            print(f"failed: {error}")
    if first_dir_id:
        print(f"\nprobing first directory id: {first_dir_id}")
        response = client.list_files_raw(
            {"parentId": first_dir_id, "page": 0, "pageSize": 100, "orderBy": 0, "sortType": 0}
        )
        summarize_response("first_dir_children", response, dump_dir)
    else:
        print("\nno directory id found from root variants")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--phone", default="13272922787")
    parser.add_argument("--auth-cache", default=".guangya_probe_auth.json")
    parser.add_argument("--dump-dir", default=".guangya_probe_dumps")
    parser.add_argument("--fresh-login", action="store_true")
    args = parser.parse_args()

    auth_cache = Path(args.auth_cache)
    dump_dir = Path(args.dump_dir) if args.dump_dir else None
    client = GuangyaProbe()
    if not args.fresh_login and client.load_auth(auth_cache):
        pass
    else:
        client.login_sms(args.phone)
        client.save_auth(auth_cache)
    probe_root(client, dump_dir)


if __name__ == "__main__":
    main()
