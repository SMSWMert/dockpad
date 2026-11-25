package com.dockpad.oneui7

data class DiscoveredPc(
    val deviceName: String,
    val ipAddress: String,
    val wsPort: Int
)

data class ProcessItem(
    val pid: Int,
    val name: String,
    val exe: String,
    val ramGb: Double,
    val ramMb: Int
)

data class MediaState(
    val active: Boolean = false,
    val title: String = "Medya Oynatılmıyor",
    val artist: String = "Monster Abra A7",
    val isPlaying: Boolean = false,
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val coverBase64: String = ""
)

data class AudioSessionItem(
    val sessionId: String,
    val name: String,
    val volume: Float,
    val mute: Boolean
)

data class ClipboardItem(
    val text: String,
    val preview: String,
    val time: String
)

data class PromptItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val text: String
)

data class NotificationItem(
    val id: String,
    val source: String,
    val sender: String,
    val summary: String,
    val time: String
)

data class WeatherInfo(
    val city: String = "Ankara",
    val tempC: Int = 6,
    val condition: String = "Açık"
)

data class PcStateUpdate(
    val deviceName: String = "Monster Abra A7",
    val cpu: Int = 0,
    val cpuGhz: Double = 0.0,
    val cpuCores: Int = 6,
    val ram: Int = 0,
    val ramUsedGb: Double = 0.0,
    val ramTotalGb: Double = 24.0,
    val ramAvailableGb: Double = 24.0,
    val ramAlertThreshold: Int = 85,
    val highRamAlert: Boolean = false,
    val gpu: Int = 0,
    val gpuTemp: Int = 0,
    val gpuName: String = "GTX 1650",
    val disk: Int = 0,
    val netDownMbps: Double = 0.0,
    val netUpMbps: Double = 0.0,
    val topProcesses: List<ProcessItem> = emptyList(),
    val media: MediaState = MediaState(),
    val audioOutput: String = "Hoparlör",
    val audioSessions: List<AudioSessionItem> = emptyList(),
    val clipboardHistory: List<ClipboardItem> = emptyList(),
    val prompts: List<PromptItem> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val weather: WeatherInfo = WeatherInfo()
)
