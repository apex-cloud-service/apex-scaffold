#!/usr/bin/env python3
"""生成 HMAC HS256 ACCESS JWT（userId/tenantId/tokenType/exp），用于本地压测认证。"""
import base64, hashlib, hmac, json, sys, time

secret = sys.argv[1].encode()
user_id = int(sys.argv[2]) if len(sys.argv) > 2 else 9001
tenant = sys.argv[3] if len(sys.argv) > 3 else "default"
exp_days = 1

def b64(d):
    return base64.urlsafe_b64encode(d).rstrip(b'=')

header = b64(json.dumps({"alg": "HS256"}, separators=(',', ':')).encode())
claims = {
    "userId": user_id,
    "tenantId": tenant,
    "tokenType": "ACCESS",
    "sub": str(user_id),
    "iat": int(time.time()),
    "exp": int(time.time()) + exp_days * 86400,
}
payload = b64(json.dumps(claims, separators=(',', ':')).encode())
signing_input = header + b'.' + payload
sig = b64(hmac.new(secret, signing_input, hashlib.sha256).digest())
print((signing_input + b'.' + sig).decode())
