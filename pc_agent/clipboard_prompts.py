import ctypes
import json
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List

import pyperclip

HISTORY_FILE = Path(__file__).resolve().parent / "clipboard_history.json"

# Windows SendInput structures for reliable Ctrl+V pasting
KEYEVENTF_KEYUP = 0x0002
VK_CONTROL = 0x11
VK_V = 0x56


class _MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", ctypes.c_long),
        ("dy", ctypes.c_long),
        ("mouseData", ctypes.c_ulong),
        ("dwFlags", ctypes.c_ulong),
        ("time", ctypes.c_ulong),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class _KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", ctypes.c_ushort),
        ("wScan", ctypes.c_ushort),
        ("dwFlags", ctypes.c_ulong),
        ("time", ctypes.c_ulong),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class _HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", ctypes.c_ulong),
        ("wParamL", ctypes.c_ushort),
        ("wParamH", ctypes.c_ushort),
    ]


class _INPUT_UNION(ctypes.Union):
    _fields_ = [
        ("mi", _MOUSEINPUT),
        ("ki", _KEYBDINPUT),
        ("hi", _HARDWAREINPUT),
    ]


class _INPUT(ctypes.Structure):
    _fields_ = [
        ("type", ctypes.c_ulong),
        ("union", _INPUT_UNION),
    ]


def send_key_combo(vk_codes: List[int]) -> None:
    inputs: List[_INPUT] = []
    for vk in vk_codes:
        inp = _INPUT()
        inp.type = 1  # INPUT_KEYBOARD
        inp.union.ki = _KEYBDINPUT(wVk=vk, wScan=0, dwFlags=0, time=0, dwExtraInfo=None)
        inputs.append(inp)
    for vk in reversed(vk_codes):
        inp = _INPUT()
        inp.type = 1
        inp.union.ki = _KEYBDINPUT(wVk=vk, wScan=0, dwFlags=KEYEVENTF_KEYUP, time=0, dwExtraInfo=None)
        inputs.append(inp)
    arr = (_INPUT * len(inputs))(*inputs)
    ctypes.windll.user32.SendInput(len(inputs), arr, ctypes.sizeof(_INPUT))


class ClipboardPromptsService:
    """Tracks persistent PC clipboard history (last 5) and serves/pastes prompt templates from config.json."""

    def __init__(self, prompts: List[Dict[str, Any]], max_history: int = 5) -> None:
        self.prompts = prompts
        self.max_history = max_history
        self._history: List[Dict[str, str]] = self._load_history()
        self._last_seen: str = self._history[0]["text"] if self._history else ""
        self.poll_clipboard()

    def _load_history(self) -> List[Dict[str, str]]:
        try:
            if HISTORY_FILE.exists():
                with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if isinstance(data, list):
                    return data[: self.max_history]
        except Exception:
            pass
        return []

    def _save_history(self) -> None:
        try:
            with open(HISTORY_FILE, "w", encoding="utf-8") as f:
                json.dump(self._history[: self.max_history], f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def update_prompts(self, prompts: List[Dict[str, Any]]) -> None:
        self.prompts = prompts

    def poll_clipboard(self) -> None:
        try:
            current = pyperclip.paste()
            if not isinstance(current, str):
                return
            cleaned = current.strip()
            if not cleaned or cleaned == self._last_seen:
                return
            self._last_seen = cleaned
            # Deduplicate existing entry
            self._history = [item for item in self._history if item["text"] != cleaned]
            self._history.insert(
                0,
                {
                    "text": cleaned,
                    "preview": (cleaned[:68] + "...") if len(cleaned) > 68 else cleaned,
                    "time": datetime.now().strftime("%H:%M"),
                },
            )
            self._history = self._history[: self.max_history]
            self._save_history()
        except Exception:
            pass

    def get_clipboard_history(self) -> List[Dict[str, str]]:
        self.poll_clipboard()
        return list(self._history)

    def get_prompts(self) -> List[Dict[str, Any]]:
        return list(self.prompts)

    def paste_text(self, text: str) -> Dict[str, Any]:
        if not text:
            return {"ok": False, "error": "Empty text"}
        try:
            pyperclip.copy(text)
            self._last_seen = text.strip()
            time.sleep(0.06)
            send_key_combo([VK_CONTROL, VK_V])
            return {"ok": True}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
