#!/usr/bin/env python3
"""Synthetic end-to-end example; Python standard library only. Creates development records."""
import datetime as dt
import http.cookiejar
import json
import os
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("BOOKING_API_URL", "http://localhost:8080").rstrip("/")
jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

def request(method, path, body=None, token=None, key=None):
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    elif method != "GET":
        headers["X-XSRF-TOKEN"] = csrf
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with client.open(req, timeout=15) as response:
            value = response.read()
            return json.loads(value) if value else None
    except urllib.error.HTTPError as error:
        raise SystemExit(f"HTTP {error.code}: {error.read().decode()}") from error

csrf = request("GET", "/api/v1/auth/csrf")["token"]
slug = "demo-" + uuid.uuid4().hex[:12]
password = "Demo-" + uuid.uuid4().hex
request("POST", "/api/v1/auth/workspaces", {"slug": slug, "name": "Portfolio Demo", "email": "owner@example.test", "password": password})
admin = request("POST", "/api/v1/auth/token", {"tenantSlug": slug, "email": "owner@example.test", "password": password})["accessToken"]
request("POST", "/api/v1/auth/register", {"tenantSlug": slug, "email": "customer@example.test", "password": password})
customer = request("POST", "/api/v1/auth/token", {"tenantSlug": slug, "email": "customer@example.test", "password": password})["accessToken"]
provider = request("POST", "/api/v1/providers", {"name": "Demo Instructor", "timeZone": "Europe/Vilnius"}, admin)
service = request("POST", "/api/v1/services", {"name": "Private lesson", "durationMinutes": 30, "cancellationHours": 24}, admin)
start = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=5)).replace(microsecond=0)
to = start + dt.timedelta(hours=3)
request("POST", f"/api/v1/providers/{provider['id']}/availability", {"startsAt": start.isoformat(), "endsAt": to.isoformat()}, admin)
from urllib.parse import urlencode
query = urlencode({"serviceId": service["id"], "from": start.isoformat(), "to": to.isoformat()})
slots = request("GET", f"/api/v1/providers/{provider['id']}/slots?{query}", token=customer)
body = {"providerId": provider["id"], "serviceId": service["id"], "startsAt": slots[0]}
key = "demo-booking-" + uuid.uuid4().hex
booking = request("POST", "/api/v1/bookings", body, customer, key)
repeat = request("POST", "/api/v1/bookings", body, customer, key)
assert repeat["id"] == booking["id"], "Retry did not return the original booking"
cancelled = request("POST", f"/api/v1/bookings/{booking['id']}/cancel", {}, customer)
assert cancelled["status"] == "CANCELLED"
request("POST", "/api/v1/auth/logout-all", {}, customer)
request("POST", "/api/v1/auth/logout-all", {}, admin)
print(json.dumps({"workspace": slug, "bookingId": booking["id"], "status": cancelled["status"], "retryVerified": True}, indent=2))
