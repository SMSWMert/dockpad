package com.dockpad.oneui7

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DockPadWebSocketClient(
    private val onConnectionChanged: (Boolean, String) -> Unit,
    private val onStateReceived: (PcStateUpdate) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var targetIp: String = ""
    private var targetPort: Int = 8765
    private var autoReconnect: Boolean = true
    private var manualDisconnect: Boolean = false
    private var isConnected: Boolean = false

    private val reconnectRunnable = Runnable {
        if (!manualDisconnect && autoReconnect && targetIp.isNotBlank() && !isConnected) {
            openSocket()
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
    }

    fun connect(ip: String, port: Int = 8765) {
        if (ip.isBlank()) return
        targetIp = ip.trim()
        targetPort = port
        manualDisconnect = false
        mainHandler.removeCallbacks(reconnectRunnable)
        closeCurrentSocket()
        openSocket()
    }

    fun disconnect() {
        manualDisconnect = true
        mainHandler.removeCallbacks(reconnectRunnable)
        closeCurrentSocket()
        isConnected = false
        mainHandler.post { onConnectionChanged(false, targetIp) }
    }

    fun isConnected(): Boolean = isConnected

    private fun closeCurrentSocket() {
        try {
            webSocket?.close(1000, "Client closing")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    private fun openSocket() {
        val url = "ws://$targetIp:$targetPort/ws"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                mainHandler.post { onConnectionChanged(true, targetIp) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "state_update") {
                        val state = parseStateUpdate(json)
                        mainHandler.post { onStateReceived(state) }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                mainHandler.post { onConnectionChanged(false, targetIp) }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                mainHandler.post { onConnectionChanged(false, targetIp) }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!manualDisconnect && autoReconnect && targetIp.isNotBlank()) {
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.postDelayed(reconnectRunnable, 3000L)
        }
    }

    private fun sendJson(obj: JSONObject) {
        try {
            webSocket?.send(obj.toString())
        } catch (_: Exception) {
        }
    }

    fun sendAction(actionId: String) {
        sendJson(JSONObject().apply {
            put("type", "action")
            put("id", actionId)
        })
    }

    fun sendMediaCmd(cmd: String) {
        sendJson(JSONObject().apply {
            put("type", "media_cmd")
            put("cmd", cmd)
        })
    }

    fun sendSetAppVolume(sessionId: String, volume: Float, mute: Boolean) {
        sendJson(JSONObject().apply {
            put("type", "set_app_volume")
            put("session_id", sessionId)
            put("volume", volume.toDouble())
            put("mute", mute)
        })
    }

    fun sendToggleAudioOutput() {
        sendJson(JSONObject().apply {
            put("type", "toggle_audio_output")
        })
    }

    fun sendPasteText(text: String) {
        sendJson(JSONObject().apply {
            put("type", "paste_text")
            put("text", text)
        })
    }

    fun sendKillProcess(pid: Int) {
        sendJson(JSONObject().apply {
            put("type", "kill_process")
            put("pid", pid)
        })
    }

    fun sendClearNotifications() {
        sendJson(JSONObject().apply {
            put("type", "clear_notifications")
        })
    }

    fun sendAddPrompt(title: String, category: String, description: String, text: String) {
        sendJson(JSONObject().apply {
            put("type", "add_prompt")
            put("title", title)
            put("category", category)
            put("description", description)
            put("text", text)
        })
    }

    fun sendSyncConfig(ramThreshold: Int) {
        sendJson(JSONObject().apply {
            put("type", "sync_config")
            put("ram_threshold", ramThreshold)
        })
    }

    private fun parseStateUpdate(json: JSONObject): PcStateUpdate {
        val procsArr = json.optJSONArray("top_processes") ?: JSONArray()
        val procs = ArrayList<ProcessItem>(procsArr.length())
        for (i in 0 until procsArr.length()) {
            val o = procsArr.getJSONObject(i)
            procs.add(
                ProcessItem(
                    pid = o.optInt("pid"),
                    name = o.optString("name"),
                    exe = o.optString("exe"),
                    ramGb = o.optDouble("ram_gb", 0.0),
                    ramMb = o.optInt("ram_mb", 0)
                )
            )
        }

        val mediaObj = json.optJSONObject("media")
        val media = if (mediaObj != null) {
            MediaState(
                active = mediaObj.optBoolean("active", false),
                title = mediaObj.optString("title", "Medya Oynatılmıyor"),
                artist = mediaObj.optString("artist", "Monster Abra A7"),
                isPlaying = mediaObj.optBoolean("is_playing", false),
                positionSec = mediaObj.optInt("position_sec", 0),
                durationSec = mediaObj.optInt("duration_sec", 0),
                coverBase64 = mediaObj.optString("cover_base64", "")
            )
        } else {
            MediaState()
        }

        val audioArr = json.optJSONArray("audio_sessions") ?: JSONArray()
        val audioSessions = ArrayList<AudioSessionItem>(audioArr.length())
        for (i in 0 until audioArr.length()) {
            val o = audioArr.getJSONObject(i)
            audioSessions.add(
                AudioSessionItem(
                    sessionId = o.optString("session_id"),
                    name = o.optString("name"),
                    volume = o.optDouble("volume", 1.0).toFloat(),
                    mute = o.optBoolean("mute", false)
                )
            )
        }

        val clipArr = json.optJSONArray("clipboard_history") ?: JSONArray()
        val clips = ArrayList<ClipboardItem>(clipArr.length())
        for (i in 0 until clipArr.length()) {
            val o = clipArr.getJSONObject(i)
            clips.add(
                ClipboardItem(
                    text = o.optString("text"),
                    preview = o.optString("preview"),
                    time = o.optString("time")
                )
            )
        }

        val promptArr = json.optJSONArray("prompts") ?: JSONArray()
        val prompts = ArrayList<PromptItem>(promptArr.length())
        for (i in 0 until promptArr.length()) {
            val o = promptArr.getJSONObject(i)
            prompts.add(
                PromptItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    category = o.optString("category"),
                    description = o.optString("description"),
                    text = o.optString("text")
                )
            )
        }

        val notifArr = json.optJSONArray("notifications") ?: JSONArray()
        val notifs = ArrayList<NotificationItem>(notifArr.length())
        for (i in 0 until notifArr.length()) {
            val o = notifArr.getJSONObject(i)
            notifs.add(
                NotificationItem(
                    id = o.optString("id"),
                    source = o.optString("source"),
                    sender = o.optString("sender"),
                    summary = o.optString("summary"),
                    time = o.optString("time")
                )
            )
        }

        val weatherObj = json.optJSONObject("weather")
        val weather = if (weatherObj != null) {
            WeatherInfo(
                city = weatherObj.optString("city", "Ankara"),
                tempC = weatherObj.optInt("temp_c", 6),
                condition = weatherObj.optString("condition", "Açık")
            )
        } else {
            WeatherInfo()
        }

        return PcStateUpdate(
            deviceName = json.optString("device_name", "Monster Abra A7"),
            cpu = json.optInt("cpu", 0),
            cpuGhz = json.optDouble("cpu_ghz", 0.0),
            cpuCores = json.optInt("cpu_cores", 6),
            ram = json.optInt("ram", 0),
            ramUsedGb = json.optDouble("ram_used_gb", 0.0),
            ramTotalGb = json.optDouble("ram_total_gb", 24.0),
            ramAvailableGb = json.optDouble("ram_available_gb", 24.0),
            ramAlertThreshold = json.optInt("ram_alert_threshold", 85),
            highRamAlert = json.optBoolean("high_ram_alert", false),
            gpu = json.optInt("gpu", 0),
            gpuTemp = json.optInt("gpu_temp", 0),
            gpuName = json.optString("gpu_name", "GTX 1650"),
            disk = json.optInt("disk", 0),
            netDownMbps = json.optDouble("net_down_mbps", 0.0),
            netUpMbps = json.optDouble("net_up_mbps", 0.0),
            topProcesses = procs,
            media = media,
            audioOutput = json.optString("audio_output", "Hoparlör"),
            audioSessions = audioSessions,
            clipboardHistory = clips,
            prompts = prompts,
            notifications = notifs,
            weather = weather
        )
    }
}
