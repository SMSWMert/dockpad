import comtypes
from ctypes import POINTER, cast
from typing import Any, Dict, List

try:
    from pycaw.pycaw import AudioUtilities, ISimpleAudioVolume, IAudioEndpointVolume
    from comtypes import CLSCTX_ALL
    PYCAW_AVAILABLE = True
except Exception:
    PYCAW_AVAILABLE = False


class AudioService:
    """Manages per-application audio sessions (pycaw) and output device switching."""

    DISPLAY_NAMES = {
        "chrome.exe": "Google Chrome",
        "msedge.exe": "Microsoft Edge",
        "firefox.exe": "Firefox",
        "spotify.exe": "Spotify",
        "vlc.exe": "VLC Medya",
        "adobe premiere pro.exe": "Premiere Pro",
        "whatsapp.exe": "WhatsApp",
    }

    def __init__(self) -> None:
        self._current_output_label = "Hoparlör"

    def get_sessions(self) -> Dict[str, Any]:
        sessions_out: List[Dict[str, Any]] = []
        output_name = self._get_default_output_name()

        if not PYCAW_AVAILABLE:
            return {
                "output_device": output_name,
                "sessions": sessions_out,
            }

        comtypes.CoInitialize()
        try:
            seen_names = set()
            sessions = AudioUtilities.GetAllSessions()
            for session in sessions:
                if not session.Process:
                    continue
                exe_name = session.Process.name()
                if not exe_name:
                    continue
                lower_exe = exe_name.lower()
                if lower_exe in seen_names:
                    continue
                seen_names.add(lower_exe)

                try:
                    vol_iface = session._ctl.QueryInterface(ISimpleAudioVolume)
                    volume = round(float(vol_iface.GetMasterVolume()), 2)
                    muted = bool(vol_iface.GetMute())
                except Exception:
                    continue

                label = self.DISPLAY_NAMES.get(
                    lower_exe, exe_name.replace(".exe", "").replace(".EXE", "")
                )
                sessions_out.append(
                    {
                        "session_id": exe_name,
                        "name": label,
                        "volume": volume,
                        "mute": muted,
                    }
                )
        except Exception:
            pass
        finally:
            comtypes.CoUninitialize()

        return {
            "output_device": output_name,
            "sessions": sessions_out[:6],
        }

    def set_app_volume(self, session_id: str, volume: float, mute: bool) -> Dict[str, Any]:
        if not PYCAW_AVAILABLE:
            return {"ok": False, "error": "pycaw unavailable"}

        comtypes.CoInitialize()
        try:
            clamped_vol = max(0.0, min(1.0, float(volume)))
            updated = 0
            for session in AudioUtilities.GetAllSessions():
                if not session.Process:
                    continue
                if session.Process.name().lower() == session_id.lower():
                    vol_iface = session._ctl.QueryInterface(ISimpleAudioVolume)
                    vol_iface.SetMasterVolume(clamped_vol, None)
                    vol_iface.SetMute(1 if mute else 0, None)
                    updated += 1
            return {"ok": updated > 0, "session_id": session_id, "volume": clamped_vol, "mute": mute}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
        finally:
            comtypes.CoUninitialize()

    def _get_default_output_name(self) -> str:
        if not PYCAW_AVAILABLE:
            return self._current_output_label
        comtypes.CoInitialize()
        try:
            device = AudioUtilities.GetSpeakers()
            if device:
                friendly = getattr(device, "FriendlyName", None)
                if friendly:
                    lower = friendly.lower()
                    if "headphone" in lower or "kulak" in lower or "headset" in lower:
                        self._current_output_label = "Kulaklık"
                    else:
                        self._current_output_label = "Hoparlör"
                    return self._current_output_label
        except Exception:
            pass
        finally:
            comtypes.CoUninitialize()
        return self._current_output_label

    def toggle_audio_output(self) -> Dict[str, Any]:
        """Toggles default audio endpoint between Headphones and Speakers via PolicyConfig COM if available, else toggles master mute fallback."""
        if not PYCAW_AVAILABLE:
            return {"ok": False, "error": "pycaw unavailable"}

        comtypes.CoInitialize()
        try:
            devices = AudioUtilities.GetAllDevices()
            render_devices = [
                d for d in devices
                if d and getattr(d, "state", None) is not None and str(d.state).endswith("ACTIVE")
                and getattr(d, "id", "").startswith("{0.0.0.")
            ]
            current_speaker = AudioUtilities.GetSpeakers()
            current_id = getattr(current_speaker, "id", "") if current_speaker else ""

            if len(render_devices) >= 2:
                next_device = next((d for d in render_devices if d.id != current_id), render_devices[0])
                if self._set_default_endpoint_com(next_device.id):
                    label = self._get_default_output_name()
                    return {"ok": True, "output_device": label}

            # Fallback: toggle master mute on current output if only 1 endpoint is active
            if current_speaker:
                interface = current_speaker.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
                endpoint_vol = cast(interface, POINTER(IAudioEndpointVolume))
                is_muted = bool(endpoint_vol.GetMute())
                endpoint_vol.SetMute(0 if is_muted else 1, None)
                return {"ok": True, "output_device": self._current_output_label, "master_muted": not is_muted}

            return {"ok": False, "error": "No active audio output devices"}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
        finally:
            comtypes.CoUninitialize()

    def toggle_master_mute(self) -> Dict[str, Any]:
        if not PYCAW_AVAILABLE:
            return {"ok": False, "error": "pycaw unavailable"}
        comtypes.CoInitialize()
        try:
            speaker = AudioUtilities.GetSpeakers()
            if not speaker:
                return {"ok": False, "error": "No speaker found"}
            interface = speaker.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
            endpoint_vol = cast(interface, POINTER(IAudioEndpointVolume))
            is_muted = bool(endpoint_vol.GetMute())
            endpoint_vol.SetMute(0 if is_muted else 1, None)
            return {"ok": True, "master_muted": not is_muted}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
        finally:
            comtypes.CoUninitialize()

    def _set_default_endpoint_com(self, device_id: str) -> bool:
        """Uses undocumented Windows IPolicyConfig COM interface to switch default playback device."""
        try:
            from ctypes import HRESULT, c_wchar_p, c_int
            from comtypes import GUID, IUnknown, COMMETHOD

            class IPolicyConfig(IUnknown):
                _iid_ = GUID("{f8679f50-850a-41cf-9c72-430f290290c8}")
                _methods_ = [
                    COMMETHOD([], HRESULT, "GetMixFormat", (["in"], c_wchar_p, "pwstrDeviceId"), (["out"], POINTER(c_int), "ppFormat")),
                    COMMETHOD([], HRESULT, "GetDeviceFormat", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "bDefault"), (["out"], POINTER(c_int), "ppFormat")),
                    COMMETHOD([], HRESULT, "ResetDeviceFormat", (["in"], c_wchar_p, "pwstrDeviceId")),
                    COMMETHOD([], HRESULT, "SetDeviceFormat", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "pEndpointFormat"), (["in"], c_int, "mixFormat")),
                    COMMETHOD([], HRESULT, "GetProcessingPeriod", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "bDefault"), (["out"], POINTER(c_int), "pmftDefaultPeriod"), (["out"], POINTER(c_int), "pmftMinimumPeriod")),
                    COMMETHOD([], HRESULT, "SetProcessingPeriod", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "pmftPeriod")),
                    COMMETHOD([], HRESULT, "GetShareMode", (["in"], c_wchar_p, "pwstrDeviceId"), (["out"], POINTER(c_int), "pMode")),
                    COMMETHOD([], HRESULT, "SetShareMode", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "mode")),
                    COMMETHOD([], HRESULT, "GetPropertyValue", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "key"), (["out"], POINTER(c_int), "pv")),
                    COMMETHOD([], HRESULT, "SetPropertyValue", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "key"), (["in"], c_int, "pv")),
                    COMMETHOD([], HRESULT, "SetDefaultEndpoint", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "role")),
                    COMMETHOD([], HRESULT, "SetEndpointVisibility", (["in"], c_wchar_p, "pwstrDeviceId"), (["in"], c_int, "bVisible")),
                ]

            clsid_policy = GUID("{870af99c-171d-4f9e-af0d-e63df40c2bc9}")
            policy_cfg = comtypes.CoCreateInstance(clsid_policy, interface=IPolicyConfig, clsctx=comtypes.CLSCTX_ALL)
            for role in (0, 1, 2):  # eConsole, eMultimedia, eCommunications
                policy_cfg.SetDefaultEndpoint(device_id, role)
            return True
        except Exception:
            return False
