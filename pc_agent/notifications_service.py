from datetime import datetime
from typing import Any, Dict, List

try:
    from winsdk.windows.ui.notifications.management import (
        UserNotificationListener,
        UserNotificationListenerAccessStatus,
    )
    from winsdk.windows.ui.notifications import KnownNotificationBindings
    WINSDK_NOTIF_AVAILABLE = True
except Exception:
    WINSDK_NOTIF_AVAILABLE = False


class NotificationsService:
    """Collects and normalizes WhatsApp and Mail notifications from Windows Action Center and API."""

    def __init__(self, max_items: int = 40) -> None:
        self.max_items = max_items
        self._items: List[Dict[str, Any]] = []
        self._seen_win_ids = set()
        self._listener_checked = False
        self._listener_allowed = False

    async def poll_windows_notifications(self) -> None:
        if not WINSDK_NOTIF_AVAILABLE:
            return
        try:
            listener = UserNotificationListener.current
            if not self._listener_checked:
                status = await listener.request_access_async()
                self._listener_allowed = status == UserNotificationListenerAccessStatus.ALLOWED
                self._listener_checked = True
            if not self._listener_allowed:
                return

            notifications = await listener.get_notifications_async(1)  # NotificationKinds.TOAST
            for notif in notifications:
                nid = notif.id
                if nid in self._seen_win_ids:
                    continue
                self._seen_win_ids.add(nid)

                app_name = ""
                try:
                    app_name = notif.app_info.display_info.display_name or ""
                except Exception:
                    pass

                sender = app_name
                body = ""
                try:
                    toast_binding = notif.notification.visual.get_binding(
                        KnownNotificationBindings.toast_generic
                    )
                    if toast_binding:
                        text_elems = list(toast_binding.get_text_elements())
                        if len(text_elems) >= 1:
                            sender = text_elems[0].text or app_name
                        if len(text_elems) >= 2:
                            body = " — ".join(t.text for t in text_elems[1:] if t.text)
                except Exception:
                    pass

                source = self._classify_source(app_name, sender, body)
                if not source:
                    continue

                self.add_notification(
                    source=source,
                    sender=sender or source,
                    summary=body or "Yeni bildirim",
                )
        except Exception:
            pass

    @staticmethod
    def _classify_source(app_name: str, sender: str = "", body: str = "") -> str:
        lower = (app_name or "").lower()
        combined = f"{lower} {(sender or '').lower()} {(body or '').lower()}"
        if "whatsapp" in lower or ("whatsapp" in combined and any(b in lower for b in ("chrome", "edge", "firefox", "telefon"))):
            return "WhatsApp"
        if any(k in lower for k in ("mail", "outlook", "thunderbird", "gmail", "posta")):
            return "Mail"
        return ""

    def add_notification(self, source: str, sender: str, summary: str, time_str: str = "") -> Dict[str, Any]:
        norm_source = "WhatsApp" if source.lower().startswith("what") else "Mail"
        item = {
            "id": f"{int(datetime.now().timestamp() * 1000)}_{len(self._items)}",
            "source": norm_source,
            "sender": sender.strip() or norm_source,
            "summary": summary.strip(),
            "time": time_str or datetime.now().strftime("%H:%M"),
        }
        self._items.insert(0, item)
        self._items = self._items[: self.max_items]
        return item

    def get_notifications(self) -> List[Dict[str, Any]]:
        return list(self._items)

    def clear_all(self) -> None:
        self._items.clear()
