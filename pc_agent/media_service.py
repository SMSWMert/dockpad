import asyncio
import base64
from typing import Any, Dict, Optional

try:
    from winsdk.windows.media.control import (
        GlobalSystemMediaTransportControlsSessionManager as MediaManager,
        GlobalSystemMediaTransportControlsSessionPlaybackStatus as PlaybackStatus,
    )
    from winsdk.windows.storage.streams import Buffer, InputStreamOptions
    WINSDK_AVAILABLE = True
except Exception:
    WINSDK_AVAILABLE = False


class MediaService:
    """Reads active Windows media session metadata/cover and sends transport commands."""

    def __init__(self) -> None:
        self._last_title: Optional[str] = None
        self._last_cover_b64: str = ""

    async def get_media_state(self) -> Dict[str, Any]:
        default_state: Dict[str, Any] = {
            "active": False,
            "title": "Medya Oynatılmıyor",
            "artist": "Monster Abra A7",
            "album": "",
            "is_playing": False,
            "position_sec": 0,
            "duration_sec": 0,
            "cover_base64": "",
            "source_app": "",
        }
        if not WINSDK_AVAILABLE:
            return default_state

        try:
            manager = await MediaManager.request_async()
            session = manager.get_current_session()
            if session is None:
                return default_state

            props = await session.try_get_media_properties_async()
            playback_info = session.get_playback_info()
            timeline = session.get_timeline_properties()

            title = (props.title or "").strip() if props else ""
            artist = (props.artist or "").strip() if props else ""
            album = (props.album_title or "").strip() if props else ""

            is_playing = False
            if playback_info and playback_info.playback_status == PlaybackStatus.PLAYING:
                is_playing = True

            position_sec = 0
            duration_sec = 0
            if timeline:
                try:
                    position_sec = max(0, int(timeline.position.total_seconds()))
                    duration_sec = max(0, int(timeline.end_time.total_seconds()))
                except Exception:
                    pass

            cover_key = f"{title}|{artist}"
            if cover_key != self._last_title:
                self._last_title = cover_key
                self._last_cover_b64 = await self._read_thumbnail_b64(props)

            return {
                "active": bool(title or artist),
                "title": title or "Bilinmeyen Parça",
                "artist": artist or "Bilinmeyen Sanatçı",
                "album": album,
                "is_playing": is_playing,
                "position_sec": position_sec,
                "duration_sec": duration_sec,
                "cover_base64": self._last_cover_b64,
                "source_app": session.source_app_user_model_id or "",
            }
        except Exception:
            return default_state

    async def _read_thumbnail_b64(self, props: Any) -> str:
        if not props or not props.thumbnail:
            return ""
        try:
            stream = await props.thumbnail.open_read_async()
            size = int(stream.size)
            if size <= 0 or size > 2 * 1024 * 1024:
                return ""
            buf = Buffer(size)
            await stream.read_async(buf, size, InputStreamOptions.READ_AHEAD)
            raw_bytes = bytes(buf)
            return base64.b64encode(raw_bytes).decode("ascii")
        except Exception:
            return ""

    async def send_command(self, cmd: str) -> Dict[str, Any]:
        if not WINSDK_AVAILABLE:
            return {"ok": False, "error": "winsdk unavailable"}
        try:
            manager = await MediaManager.request_async()
            session = manager.get_current_session()
            if session is None:
                return {"ok": False, "error": "No active media session"}

            if cmd == "play_pause":
                await session.try_toggle_play_pause_async()
            elif cmd == "next":
                await session.try_skip_next_async()
            elif cmd == "prev":
                await session.try_skip_previous_async()
            else:
                return {"ok": False, "error": f"Unknown media command: {cmd}"}
            return {"ok": True, "cmd": cmd}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
