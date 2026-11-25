import json
import time
import urllib.request
from typing import Any, Dict


WMO_TR = {
    0: "Açık",
    1: "Az Bulutlu",
    2: "Parçalı Bulutlu",
    3: "Bulutlu",
    45: "Sisli",
    48: "Kırağılı Sis",
    51: "Hafif Çise",
    53: "Çise",
    55: "Yoğun Çise",
    61: "Hafif Yağmur",
    63: "Yağmurlu",
    65: "Şiddetli Yağmur",
    71: "Hafif Kar",
    73: "Kar Yağışlı",
    75: "Yoğun Kar",
    80: "Sağanak",
    95: "Fırtına",
}


class WeatherService:
    """Fetches live weather via Open-Meteo (no API key) with 15-minute cache."""

    def __init__(self, fallback_weather: Dict[str, Any]) -> None:
        self._fallback = fallback_weather or {"city": "Ankara", "temp_c": 6, "condition": "Açık"}
        self._cached: Dict[str, Any] = dict(self._fallback)
        self._last_fetch: float = 0.0
        self._lat = float(self._fallback.get("lat", 39.9334))
        self._lon = float(self._fallback.get("lon", 32.8597))

    def update_config(self, weather_cfg: Dict[str, Any]) -> None:
        if weather_cfg:
            self._fallback = weather_cfg
            self._lat = float(weather_cfg.get("lat", 39.9334))
            self._lon = float(weather_cfg.get("lon", 32.8597))
            self._last_fetch = 0.0

    def get_weather(self) -> Dict[str, Any]:
        now = time.time()
        if now - self._last_fetch < 900 and self._last_fetch > 0:
            return self._cached

        try:
            url = (
                f"https://api.open-meteo.com/v1/forecast"
                f"?latitude={self._lat}&longitude={self._lon}"
                f"&current=temperature_2m,weather_code&timezone=auto"
            )
            req = urllib.request.Request(url, headers={"User-Agent": "DockPad/1.0"})
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            current = data.get("current", {})
            temp = round(float(current.get("temperature_2m", self._fallback.get("temp_c", 6))))
            code = int(current.get("weather_code", 0))
            cond = WMO_TR.get(code, self._fallback.get("condition", "Açık"))
            self._cached = {
                "city": self._fallback.get("city", "Ankara"),
                "temp_c": temp,
                "condition": cond,
            }
            self._last_fetch = now
        except Exception:
            self._last_fetch = now - 780  # retry in 2 minutes on failure
        return self._cached
