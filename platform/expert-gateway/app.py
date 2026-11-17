import asyncio
from contextlib import asynccontextmanager
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import time
from urllib.parse import quote, urlencode
from uuid import UUID

import httpx
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from websockets.asyncio.client import connect
from websockets.exceptions import InvalidStatus, WebSocketException

config_path = Path(os.environ.get("OCTOP_GATEWAY_CONFIG", "config.json"))
config = json.loads(config_path.read_text(encoding="utf-8-sig"))
upstream = config["upstream"].rstrip("/")
socket_base = upstream.replace("http", "ws", 1)
MODE = {"ask", "plan", "craft"}


class OctopAuth:
    def __init__(self, client):
        self.client = client
        self.token = None
        self.refresh_at = 0
        self.lock = asyncio.Lock()

    async def get(self, rejected=None):
        async with self.lock:
            if self.token and self.token != rejected and time.monotonic() < self.refresh_at:
                return self.token
            response = await self.client.post(upstream + "/api/auth/login", json=config["octop"])
            response.raise_for_status()
            data = response.json()
            token = data["access_token"]
            if not isinstance(token, str) or not token:
                raise ValueError("Octop login did not return a token")
            self.token = token
            self.refresh_at = time.monotonic() + max(0, int(data["expires_in"]) - 60)
            return token

    async def socket(self, expert):
        token = await self.get()
        for attempt in range(2):
            url = f"{socket_base}/api/agents/{quote(expert, safe='')}/chat/ws?{urlencode({'token': token})}"
            try:
                return await connect(url, open_timeout=8, close_timeout=2, max_size=2**22)
            except InvalidStatus as error:
                if attempt or error.response.status_code not in (401, 403):
                    raise
                token = await self.get(rejected=token)


@asynccontextmanager
async def lifespan(app):
    async with httpx.AsyncClient(timeout=15, trust_env=False) as client:
        app.state.octop = OctopAuth(client)
        yield


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


@app.get("/health")
async def health():
    return {"status": "ok"}


def get_app(headers):
    name = headers.get("x-octop-app", "")
    entry = config["apps"].get(name)
    authorization = headers.get("authorization", "")
    if not entry or not authorization.startswith("Bearer "):
        return None
    digest = hashlib.sha256(authorization[7:].encode()).hexdigest()
    if not hmac.compare_digest(digest, entry["sha256"]):
        return None
    return name, entry


def make_turn(payload, app_id, expert):
    user = payload["user_id"]
    conversation = UUID(payload["conversation_id"])
    text = payload["text"].strip()
    mode = payload.get("conversation_mode", "craft")
    if (
        payload.get("type") != "user_turn"
        or not re.fullmatch(r"[a-zA-Z0-9_.@-]{1,128}", user)
        or not 1 <= len(text) <= 16000
        or mode not in MODE
    ):
        raise ValueError("invalid turn")
    namespace = json.dumps([app_id, expert, user, str(conversation)], separators=(",", ":"))
    session_key = "expert-gateway:" + hashlib.sha256(namespace.encode()).hexdigest()
    return {
        "type": "user_turn", "text": text, "session_key": session_key,
        "conversation_mode": mode, "reasoning_mode": "enabled", "delivery": "follow_up",
        **({"thread_id": payload["thread_id"]} if payload.get("thread_id") else {}),
    }


async def relay(client, server):
    state = {"thread": None, "active": False}

    async def send_to_client():
        async for raw in server:
            event = json.loads(raw)
            kind = event.get("type")
            if kind == "turn.started":
                state.update(active=True, thread=event.get("thread_id"))
            if kind in ("turn.ended", "error", "hitl_required"):
                state["active"] = False
            await client.send_text(raw)
            if kind in ("turn.ended", "error", "hitl_required") or (
                kind == "steer.ack" and event.get("status") == "rejected"
            ):
                return

    async def wait_for_cancel():
        while True:
            event = await client.receive_json()
            if event.get("type") == "cancel":
                return

    outgoing = asyncio.create_task(send_to_client())
    incoming = asyncio.create_task(wait_for_cancel())
    try:
        done, _ = await asyncio.wait({outgoing, incoming}, timeout=125,
                                     return_when=asyncio.FIRST_COMPLETED)
        if outgoing in done:
            outgoing.result()
        if state["active"] and state["thread"]:
            await server.send(json.dumps({"type": "cancel", "thread_id": state["thread"]}))
        if not done:
            await client.send_json({"type": "error", "message": "等待 Octop 回复超时"})
    finally:
        for task in (outgoing, incoming):
            if not task.done():
                task.cancel()
        await asyncio.gather(outgoing, incoming, return_exceptions=True)


@app.websocket("/v1/experts/{expert}/stream")
async def expert_stream(client: WebSocket, expert: str):
    identity = get_app(client.headers)
    if not identity or expert not in identity[1]["experts"]:
        await client.close(code=1008)
        return
    await client.accept()
    try:
        payload = await asyncio.wait_for(client.receive_json(), timeout=8)
        turn = make_turn(payload, identity[0], expert)
    except (ValueError, TypeError, AttributeError, KeyError, TimeoutError):
        await client.send_json({"type": "error", "message": "请求参数无效"})
        await client.close()
        return
    except WebSocketDisconnect:
        return

    server = None
    try:
        server = await app.state.octop.socket(expert)
        await server.send(json.dumps(turn))
        await relay(client, server)
    except WebSocketDisconnect:
        return
    except (ValueError, KeyError, httpx.HTTPError, WebSocketException, OSError, TimeoutError):
        try:
            await client.send_json({"type": "error", "message": "Octop 接入服务暂时无法完成请求"})
        except (RuntimeError, WebSocketDisconnect, OSError):
            pass
    finally:
        if server is not None:
            await server.close()
        try:
            await client.close()
        except (RuntimeError, WebSocketDisconnect, OSError):
            pass
