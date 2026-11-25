import asyncio
import json
import os
import socket
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Set

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import BaseModel

from actions_service import ActionsService
from audio_service import AudioService
from clipboard_prompts import ClipboardPromptsService
from media_service import MediaService
from notifications_service import NotificationsService
from system_service import SystemService

BASE_DIR = Path(__file__).resolve().parent
CONFIG_PATH = BASE_DIR / "config.json"


def load_config() -> Dict[str, Any]:
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


config = load_config()

system_service = SystemService(ram_alert_threshold=config.get("ram_alert_threshold", 85))
media_service = MediaService()
audio_service = AudioService()
clipboard_service = ClipboardPromptsService(prompts=config.get("prompts", []))
notifications_service = NotificationsService()
actions_service = ActionsService(actions=config.get("actions", []), audio_service=audio_service)

app = FastAPI(title="One UI 7 Dock Pad PC Agent", version="1.0.0")
connected_clients: Set[WebSocket] = set()
_udp_stop_event = threading.Event()


def udp_broadcast_loop() -> None:
    """Broadcasts PC presence on UDP port 5005 so Turkcell T Tablet discovers Monster Abra A7 automatically."""
    udp_port = int(config.get("udp_broadcast_port", 5005))
    ws_port = int(config.get("ws_port", 8765))
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(1.0)

    while not _udp_stop_event.is_set():
        try:
            payload = json.dumps(
                {
                    "service": "dockpad_pc_agent",
                    "device_name": config.get("device_name", "Monster Abra A7"),
                    "ws_port": ws_port,
                    "http_port": ws_port,
                }
            ).encode("utf-8")
            sock.sendto(payload, ("255.255.255.255", udp_port))
        except Exception:
            pass
        _udp_stop_event.wait(2.0)

    sock.close()


async def _run_winrt_safe(coro_fn: Any, fallback: Any, timeout_sec: float = 2.5) -> Any:
    try:
        return await asyncio.wait_for(
            asyncio.to_thread(lambda: asyncio.run(coro_fn())),
            timeout=timeout_sec,
        )
    except Exception:
        return fallback


async def build_state_payload() -> Dict[str, Any]:
    telemetry = await asyncio.to_thread(system_service.get_telemetry)
    media_state = await _run_winrt_safe(
        media_service.get_media_state,
        {
            "active": False,
            "title": "Medya Oynatılmıyor",
            "artist": "Monster Abra A7",
            "album": "",
            "is_playing": False,
            "position_sec": 0,
            "duration_sec": 0,
            "cover_base64": "",
            "source_app": "",
        },
    )
    audio_state = await asyncio.to_thread(audio_service.get_sessions)
    clipboard_history = await asyncio.to_thread(clipboard_service.get_clipboard_history)
    await _run_winrt_safe(notifications_service.poll_windows_notifications, None)

    return {
        "type": "state_update",
        "device_name": config.get("device_name", "Monster Abra A7"),
        "cpu": telemetry["cpu"],
        "cpu_ghz": telemetry["cpu_ghz"],
        "cpu_cores": telemetry["cpu_cores"],
        "ram": telemetry["ram"],
        "ram_used_gb": telemetry["ram_used_gb"],
        "ram_total_gb": telemetry["ram_total_gb"],
        "ram_available_gb": telemetry["ram_available_gb"],
        "ram_alert_threshold": telemetry["ram_alert_threshold"],
        "high_ram_alert": telemetry["high_ram_alert"],
        "top_processes": telemetry["top_processes"],
        "media": media_state,
        "audio_output": audio_state["output_device"],
        "audio_sessions": audio_state["sessions"],
        "clipboard_history": clipboard_history,
        "prompts": clipboard_service.get_prompts(),
        "actions": actions_service.get_actions(),
        "notifications": notifications_service.get_notifications(),
        "weather": config.get("weather", {"city": "Ankara", "temp_c": 6, "condition": "Açık"}),
    }


async def broadcast_state() -> None:
    if not connected_clients:
        return
    payload = await build_state_payload()
    message = json.dumps(payload, ensure_ascii=False)
    dead: List[WebSocket] = []
    for ws in list(connected_clients):
        try:
            await ws.send_text(message)
        except Exception:
            dead.append(ws)
    for ws in dead:
        connected_clients.discard(ws)


async def periodic_broadcaster() -> None:
    while True:
        try:
            await broadcast_state()
        except Exception:
            pass
        await asyncio.sleep(1.2)


@app.on_event("startup")
async def on_startup() -> None:
    _udp_stop_event.clear()
    threading.Thread(target=udp_broadcast_loop, daemon=True).start()
    asyncio.create_task(periodic_broadcaster())


@app.on_event("shutdown")
async def on_shutdown() -> None:
    _udp_stop_event.set()


class NotifyRequest(BaseModel):
    source: str = "WhatsApp"
    sender: str
    summary: str
    time: str = ""


@app.get("/health")
async def health() -> Dict[str, Any]:
    return {
        "status": "ok",
        "device_name": config.get("device_name", "Monster Abra A7"),
        "ws_clients": len(connected_clients),
    }


@app.post("/api/notify")
async def push_notification(req: NotifyRequest) -> Dict[str, Any]:
    item = notifications_service.add_notification(
        source=req.source,
        sender=req.sender,
        summary=req.summary,
        time_str=req.time,
    )
    await broadcast_state()
    return {"ok": True, "item": item}


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket) -> None:
    await websocket.accept()
    connected_clients.add(websocket)
    try:
        # Send immediate initial state snapshot upon connection
        initial_payload = await build_state_payload()
        await websocket.send_text(json.dumps(initial_payload, ensure_ascii=False))

        while True:
            raw = await websocket.receive_text()
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue

            msg_type = msg.get("type", "")
            result: Dict[str, Any] = {"ok": True}

            if msg_type == "action":
                result = await asyncio.to_thread(actions_service.execute, str(msg.get("id", "")))

            elif msg_type == "media_cmd":
                cmd_val = str(msg.get("cmd", ""))
                result = await _run_winrt_safe(
                    lambda: media_service.send_command(cmd_val),
                    {"ok": False, "error": "Media command timeout"},
                )

            elif msg_type == "set_app_volume":
                result = await asyncio.to_thread(
                    audio_service.set_app_volume,
                    str(msg.get("session_id", "")),
                    float(msg.get("volume", 1.0)),
                    bool(msg.get("mute", False)),
                )

            elif msg_type == "toggle_audio_output":
                result = await asyncio.to_thread(audio_service.toggle_audio_output)

            elif msg_type == "paste_text":
                result = await asyncio.to_thread(clipboard_service.paste_text, str(msg.get("text", "")))

            elif msg_type == "kill_process":
                result = await asyncio.to_thread(system_service.kill_process, int(msg.get("pid", 0)))

            elif msg_type == "clear_notifications":
                notifications_service.clear_all()

            elif msg_type == "sync_config":
                global config
                config = load_config()
                if "ram_threshold" in msg:
                    system_service.set_ram_threshold(int(msg["ram_threshold"]))
                else:
                    system_service.set_ram_threshold(int(config.get("ram_alert_threshold", 85)))
                clipboard_service.update_prompts(config.get("prompts", []))
                actions_service.reload(config.get("actions", []))

            await websocket.send_text(json.dumps({"type": "ack", "for": msg_type, "result": result}, ensure_ascii=False))
            await broadcast_state()

    except WebSocketDisconnect:
        connected_clients.discard(websocket)
    except Exception:
        connected_clients.discard(websocket)


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(config.get("ws_port", 8765)),
        reload=False,
        log_level="info",
    )
