#!/usr/bin/env python3
"""Run after starting the app. Uses fictional local data and the standard library only."""
import hashlib
import hmac
import json
import os
import urllib.request
import uuid

BASE = "http://127.0.0.1:8080"
SECRET = os.environ.get("WEBHOOK_SECRET", "test-secret-change-me").encode()

def request(path, body=None, headers=None):
    req = urllib.request.Request(BASE + path, data=body, headers=headers or {},
                                 method="POST" if body is not None else "GET")
    with urllib.request.urlopen(req, timeout=15) as response:
        return response.status, response.read()

status, created = request("/api/accounts", b'{"ownerName":"Asha"}', {"Content-Type": "application/json"})
assert status == 201
account = json.loads(created)
payload = json.dumps({"accountId": account["id"], "amountMinor": 1000,
                      "currency": "INR", "eventId": "evt_123"}, separators=(",", ":")).encode()
headers = {"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4()),
           "X-Signature": hmac.new(SECRET, payload, hashlib.sha256).hexdigest()}
first = request("/api/webhooks/payments", payload, headers)
second = request("/api/webhooks/payments", payload, headers)
assert first[0] == second[0] == 200 and first[1] == second[1]
_, entries = request("/api/accounts/" + account["id"] + "/entries")
assert len(json.loads(entries)) == 1
_, current = request("/api/accounts/" + account["id"])
assert json.loads(current)["balance"] == 1000
status, reconciled = request("/api/admin/reconcile", b"")
assert status == 200 and json.loads(reconciled)["ok"] is True
print(json.dumps({"account": account["id"], "payment": json.loads(first[1]),
                  "replayMatches": True, "entries": json.loads(entries),
                  "reconciled": json.loads(reconciled)}, indent=2))
