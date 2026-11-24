import psutil
from typing import Any, Dict, List


class SystemService:
    """Collects CPU, RAM, and top-process telemetry for Monster Abra A7."""

    PROTECTED_PROCESSES = {
        "system",
        "system idle process",
        "registry",
        "smss.exe",
        "csrss.exe",
        "wininit.exe",
        "services.exe",
        "lsass.exe",
        "svchost.exe",
        "winlogon.exe",
        "dwm.exe",
        "explorer.exe",
        "python.exe",
    }

    DISPLAY_NAME_MAP = {
        "chrome.exe": "Google Chrome",
        "msedge.exe": "Microsoft Edge",
        "firefox.exe": "Mozilla Firefox",
        "adobe premiere pro.exe": "Adobe Premiere Pro",
        "afterfx.exe": "Adobe After Effects",
        "photoshop.exe": "Adobe Photoshop",
        "antigravity.exe": "Antigravity",
        "code.exe": "VS Code",
        "spotify.exe": "Spotify",
        "whatsapp.exe": "WhatsApp",
        "olk.exe": "Outlook",
        "outlook.exe": "Outlook",
    }

    def __init__(self, ram_alert_threshold: int = 85) -> None:
        self.ram_alert_threshold = ram_alert_threshold
        # Prime psutil cpu_percent so subsequent non-blocking calls return accurate values
        psutil.cpu_percent(interval=None)

    def set_ram_threshold(self, threshold: int) -> None:
        self.ram_alert_threshold = max(50, min(99, int(threshold)))

    def get_telemetry(self, top_n: int = 8) -> Dict[str, Any]:
        cpu_pct = round(psutil.cpu_percent(interval=None))
        cpu_freq = psutil.cpu_freq()
        cpu_ghz = round((cpu_freq.current / 1000.0), 1) if cpu_freq and cpu_freq.current else 0.0
        cpu_cores = psutil.cpu_count(logical=False) or psutil.cpu_count(logical=True) or 8

        vm = psutil.virtual_memory()
        ram_pct = round(vm.percent)
        ram_total_gb = round(vm.total / (1024 ** 3), 1)
        ram_used_gb = round((vm.total - vm.available) / (1024 ** 3), 1)
        ram_avail_gb = round(vm.available / (1024 ** 3), 1)

        top_processes = self._get_top_processes(top_n=top_n)

        return {
            "cpu": cpu_pct,
            "cpu_ghz": cpu_ghz,
            "cpu_cores": cpu_cores,
            "ram": ram_pct,
            "ram_used_gb": ram_used_gb,
            "ram_total_gb": ram_total_gb,
            "ram_available_gb": ram_avail_gb,
            "ram_alert_threshold": self.ram_alert_threshold,
            "high_ram_alert": ram_pct >= self.ram_alert_threshold,
            "top_processes": top_processes,
        }

    def _get_top_processes(self, top_n: int = 8) -> List[Dict[str, Any]]:
        grouped: Dict[str, Dict[str, Any]] = {}
        for proc in psutil.process_iter(["pid", "name", "memory_info", "cpu_percent"]):
            try:
                info = proc.info
                raw_name = (info.get("name") or "").strip()
                if not raw_name:
                    continue
                lower_name = raw_name.lower()
                if lower_name in self.PROTECTED_PROCESSES:
                    continue
                mem_info = info.get("memory_info")
                if not mem_info:
                    continue
                rss_bytes = mem_info.rss
                if rss_bytes < 40 * 1024 * 1024:  # Ignore tiny < 40 MB processes
                    continue

                display_name = self.DISPLAY_NAME_MAP.get(
                    lower_name, raw_name.replace(".exe", "").replace(".EXE", "")
                )

                # Group multi-process apps (like Chrome) under one entry keeping highest-RSS PID
                if lower_name not in grouped:
                    grouped[lower_name] = {
                        "pid": info["pid"],
                        "name": display_name,
                        "exe": raw_name,
                        "ram_bytes": rss_bytes,
                        "ram_gb": round(rss_bytes / (1024 ** 3), 2),
                        "ram_mb": round(rss_bytes / (1024 ** 2)),
                        "pids": [info["pid"]],
                    }
                else:
                    entry = grouped[lower_name]
                    entry["ram_bytes"] += rss_bytes
                    entry["ram_gb"] = round(entry["ram_bytes"] / (1024 ** 3), 2)
                    entry["ram_mb"] = round(entry["ram_bytes"] / (1024 ** 2))
                    entry["pids"].append(info["pid"])
            except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                continue

        sorted_procs = sorted(grouped.values(), key=lambda x: x["ram_bytes"], reverse=True)
        result: List[Dict[str, Any]] = []
        for item in sorted_procs[:top_n]:
            result.append(
                {
                    "pid": item["pid"],
                    "name": item["name"],
                    "exe": item["exe"],
                    "ram_gb": item["ram_gb"],
                    "ram_mb": item["ram_mb"],
                }
            )
        return result

    def kill_process(self, pid: int) -> Dict[str, Any]:
        try:
            target = psutil.Process(int(pid))
            proc_name = target.name()
            if proc_name.lower() in self.PROTECTED_PROCESSES:
                return {"ok": False, "error": f"Protected process: {proc_name}"}

            # Terminate all processes sharing the same executable (e.g. Chrome renderer tree)
            killed_count = 0
            for proc in psutil.process_iter(["pid", "name"]):
                try:
                    if proc.info["name"] and proc.info["name"].lower() == proc_name.lower():
                        proc.terminate()
                        killed_count += 1
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    continue

            return {"ok": True, "killed": proc_name, "count": killed_count}
        except psutil.NoSuchProcess:
            return {"ok": False, "error": "Process not found"}
        except psutil.AccessDenied:
            return {"ok": False, "error": "Access denied"}
        except Exception as exc:
            return {"ok": False, "error": str(exc)}
