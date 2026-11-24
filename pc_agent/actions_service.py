import os
import subprocess
import webbrowser
from typing import Any, Dict, List

from clipboard_prompts import send_key_combo


VK_MAP = {
    "WIN": 0x5B,
    "LWIN": 0x5B,
    "CTRL": 0x11,
    "CONTROL": 0x11,
    "SHIFT": 0x10,
    "ALT": 0x12,
    "PRINTSCREEN": 0x2C,
    "S": 0x53,
    "C": 0x43,
    "V": 0x56,
    "Z": 0x5A,
    "TAB": 0x09,
    "ENTER": 0x0D,
    "ESC": 0x1B,
}


class ActionsService:
    """Executes configurable quick-command actions from config.json."""

    def __init__(self, actions: List[Dict[str, Any]], audio_service: Any = None) -> None:
        self._actions = {a["id"]: a for a in actions}
        self._audio_service = audio_service

    def reload(self, actions: List[Dict[str, Any]]) -> None:
        self._actions = {a["id"]: a for a in actions}

    def get_actions(self) -> List[Dict[str, Any]]:
        return list(self._actions.values())

    def execute(self, action_id: str) -> Dict[str, Any]:
        action = self._actions.get(action_id)
        if not action:
            return {"ok": False, "error": f"Unknown action id: {action_id}"}

        action_type = action.get("type", "").lower()
        target = action.get("target", "")

        try:
            if action_type == "url":
                webbrowser.open(target)
                return {"ok": True, "action": action_id}

            if action_type == "app":
                expanded = os.path.expandvars(target)
                if os.path.exists(expanded):
                    os.startfile(expanded)
                else:
                    subprocess.Popen(expanded, shell=True)
                return {"ok": True, "action": action_id}

            if action_type == "command":
                expanded = os.path.expandvars(target)
                subprocess.Popen(expanded, shell=True)
                return {"ok": True, "action": action_id}

            if action_type == "hotkey":
                parts = [p.strip().upper() for p in target.split("+") if p.strip()]
                vks = [VK_MAP[p] for p in parts if p in VK_MAP]
                if not vks:
                    return {"ok": False, "error": f"Invalid hotkey: {target}"}
                send_key_combo(vks)
                return {"ok": True, "action": action_id}

            if action_type == "internal" and target == "toggle_audio_output":
                if self._audio_service:
                    return self._audio_service.toggle_audio_output()
                return {"ok": False, "error": "Audio service unavailable"}

            return {"ok": False, "error": f"Unsupported action type: {action_type}"}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
