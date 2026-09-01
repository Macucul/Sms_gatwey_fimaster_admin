from fastapi import FastAPI, Request, Header, HTTPException
from pydantic import BaseModel, Field
from typing import Optional
import os
import time
import hmac
import hashlib
import json
import httpx
import aioredis

# Configuration via environment
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
SHARED_SECRET = os.getenv("GATEWAY_SECRET", "changeme")
FIREBASE_BASE = os.getenv("FIREBASE_BASE", "")  # e.g. https://<db>.firebaseio.com
FIREBASE_AUTH = os.getenv("FIREBASE_AUTH", "")
IDEMPOTENCY_TTL = int(os.getenv("IDEMPOTENCY_TTL", 60 * 60 * 24 * 7))  # 7 days

app = FastAPI(title="Sms Gatwey FiMaster Admin - Event Gateway")
redis = None


class EventPayload(BaseModel):
    event: str = Field(..., description="Event name/type")
    login: str = Field(..., description="MT5 account or user id")
    timestamp: Optional[int] = Field(None, description="Unix timestamp in seconds")
    event_id: Optional[str] = Field(None, description="Optional event id (canonical)")
    msg: Optional[str] = None
    symbol: Optional[str] = None
    price: Optional[float] = None
    ticket: Optional[string] if False else Optional[str]  # type: ignore - see below


class UserCreate(BaseModel):
    id: str
    phone: Optional[str] = None
    mt5: Optional[str] = None
    password_hash: Optional[str] = None
    salt: Optional[str] = None


@app.on_event("startup")
async def startup_event():
    global redis
    redis = await aioredis.from_url(REDIS_URL)


def generate_canonical_id(event: str, ts: int, login: str) -> str:
    base = f"{ts}-{login}-{event}"
    digest = hashlib.md5(base.encode("utf-8")).hexdigest()
    return f"{ts}-{digest}"


def verify_signature(body: bytes, signature_header: Optional[str]) -> bool:
    """
    Expecting header value either raw hex or prefixed 'sha256=<hex>'
    """
    if not signature_header:
        return False
    sig = signature_header
    if signature_header.startswith("sha256="):
        sig = signature_header.split("=", 1)[1]
    expected = hmac.new(SHARED_SECRET.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(sig, expected)


async def persist_event_to_firebase(ev: dict) -> bool:
    """If FIREBASE_BASE configured, write event to /dados/eventos/<event_id>.json and for ping set /dados/status/<login>.json"""
    if not FIREBASE_BASE:
        return False
    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            # if event seems like status/ping, write to status node instead
            etype = (ev.get("event") or "").lower()
            if "ping" in etype or etype == "status":
                url = f"{FIREBASE_BASE.rstrip('/')}/dados/status/{ev.get('login')}.json"
                params = {"auth": FIREBASE_AUTH} if FIREBASE_AUTH else None
                await client.put(url, params=params, json=ev)
                return True
            else:
                url = f"{FIREBASE_BASE.rstrip('/')}/dados/eventos/{ev.get('event_id')}.json"
                params = {"auth": FIREBASE_AUTH} if FIREBASE_AUTH else None
                await client.put(url, params=params, json=ev)
                return True
        except Exception as e:
            print("firebase persist error:", e)
            return False


@app.post("/events")
async def ingest_event(request: Request, x_signature: Optional[str] = Header(None)):
    body = await request.body()
    # Basic signature validation
    if not verify_signature(body, x_signature):
        raise HTTPException(status_code=401, detail="Invalid signature")

    try:
        payload = json.loads(body)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON")

    # minimal schema enforcement
    event = payload.get("event")
    login = payload.get("login")
    if not event or not login:
        raise HTTPException(status_code=400, detail="Missing required fields: event and login")

    ts = int(payload.get("timestamp") or int(time.time()))
    ev_id = payload.get("event_id") or generate_canonical_id(event, ts, login)

    key = f"evt:{ev_id}"
    # idempotency via Redis SETNX
    created = await redis.setnx(key, "1")
    if created:
        await redis.expire(key, IDEMPOTENCY_TTL)
        # enrich payload
        payload["event_id"] = ev_id
        payload["timestamp"] = ts
        # persist to firebase if configured (best-effort)
        _ = await persist_event_to_firebase(payload)
        # TODO: persist to internal DB / audit log
        return {"event_id": ev_id, "created": True}
    else:
        return {"event_id": ev_id, "created": False, "message": "duplicate"}


@app.post("/users")
async def create_user(user: UserCreate, x_admin_token: Optional[str] = Header(None)):
    # Admin-only endpoint; validate admin token (simple header compare for now)
    admin_token = os.getenv("ADMIN_TOKEN")
    if admin_token and x_admin_token != admin_token:
        raise HTTPException(status_code=403, detail="Forbidden")

    # Basic create flow: write to Firebase indices and usuarios if FIREBASE_BASE configured
    created = False
    if FIREBASE_BASE:
        user_json = user.dict()
        # write user file
        url = f"{FIREBASE_BASE.rstrip('/')}/dados/usuarios/{user.id}.json"
        params = {"auth": FIREBASE_AUTH} if FIREBASE_AUTH else None
        async with httpx.AsyncClient(timeout=10.0) as client:
            await client.put(url, params=params, json=user_json)
            # Also update indices by mt5 or phone if provided
            if user.mt5:
                mt5_url = f"{FIREBASE_BASE.rstrip('/')}/dados/indices/mt5/{user.mt5}.json"
                await client.put(mt5_url, params=params, json={"usuario": user.id})
            if user.phone:
                phone_url = f"{FIREBASE_BASE.rstrip('/')}/dados/indices/telefones/{user.phone}.json"
                await client.put(phone_url, params=params, json={"usuario": user.id})
        created = True
    # TODO: store in internal DB and return metadata
    return {"id": user.id, "created": created}


@app.get("/health")
async def health():
    return {"status": "ok"}
