package com.dockpad.oneui7

import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SCREEN_PAIRING = 0
        private const val SCREEN_DASHBOARD = 1
        private const val SCREEN_NOTIFICATIONS = 2
        private const val SCREEN_SYSTEM_MONITOR = 3
        private const val SCREEN_PROMPTS_VAULT = 4
        private const val SCREEN_STANDBY = 5
        private const val SCREEN_SETTINGS = 6
    }

    private lateinit var settings: SettingsManager
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var udpClient: UdpDiscoveryClient
    private lateinit var wsClient: DockPadWebSocketClient

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentState: PcStateUpdate = PcStateUpdate()
    private var discoveredPc: DiscoveredPc? = null
    private var activePromptFilter: String = "Tümü"
    private var lastCoverHash: Int = 0

    // Adapters
    private lateinit var audioAdapter: AudioSessionAdapter
    private lateinit var whatsappAdapter: NotificationAdapter
    private lateinit var mailAdapter: NotificationAdapter
    private lateinit var processAdapter: ProcessAdapter
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var promptAdapter: PromptAdapter

    // Clock & Idle Runnables
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClockViews()
            applyScreenFlags()
            mainHandler.postDelayed(this, 15_000L)
        }
    }

    private val standbyIdleRunnable = Runnable {
        if (viewFlipper.displayedChild == SCREEN_DASHBOARD) {
            showScreen(SCREEN_STANDBY)
        }
    }

    // Views - Pairing
    private lateinit var tvPairingClock: TextView
    private lateinit var pillPairingMail: TextView
    private lateinit var pillPairingWhatsapp: TextView
    private lateinit var tvDiscoveredName: TextView
    private lateinit var tvDiscoveredIp: TextView
    private lateinit var btnConnectPc: TextView
    private lateinit var etManualIp: EditText

    // Views - Dashboard
    private lateinit var tvDashClock: TextView
    private lateinit var pillDashMail: TextView
    private lateinit var pillDashWhatsapp: TextView
    private lateinit var dotDashConnection: View
    private lateinit var tvDashConnection: TextView
    private lateinit var tvDashCpu: TextView
    private lateinit var tvDashRam: TextView
    private lateinit var ivDashAlbumArt: ImageView
    private lateinit var tvDashMediaTitle: TextView
    private lateinit var tvDashMediaArtist: TextView
    private lateinit var btnDashPlayPause: ImageView
    private lateinit var tvDashAudioOutput: TextView
    private lateinit var tvDashClip1: TextView
    private lateinit var tvDashPrompt1: TextView

    // Views - Notifications
    private lateinit var tvNotifClock: TextView
    private lateinit var switchNotifPrivacy: SwitchCompat

    // Views - System Monitor
    private lateinit var tvSysClock: TextView
    private lateinit var pillSysRamWarning: LinearLayout
    private lateinit var tvSysRamWarning: TextView
    private lateinit var tvSysStatusLabel: TextView
    private lateinit var gaugeCpu: CircularGaugeView
    private lateinit var gaugeRam: CircularGaugeView
    private lateinit var tvSysCpuBig: TextView
    private lateinit var tvSysCpuSub: TextView
    private lateinit var tvSysRamBig: TextView
    private lateinit var tvSysRamUsedTotal: TextView
    private lateinit var tvSysRamAvail: TextView

    // Views - Prompts Vault
    private lateinit var tvVaultClock: TextView
    private lateinit var tvVaultTelemetry: TextView
    private lateinit var chipAll: TextView
    private lateinit var chipCodex: TextView
    private lateinit var chipAntigravity: TextView
    private lateinit var chipAds: TextView

    // Views - StandBy
    private lateinit var tvStandbyClock: TextView
    private lateinit var tvStandbyDate: TextView
    private lateinit var tvStandbyWeather: TextView
    private lateinit var ivStandbyAlbumArt: ImageView
    private lateinit var tvStandbyMediaTitle: TextView
    private lateinit var tvStandbyMediaArtist: TextView
    private lateinit var pbStandbyMedia: ProgressBar
    private lateinit var btnStandbyPlayPause: ImageView
    private lateinit var pillStandbyNotifications: TextView
    private lateinit var dotStandbyConn: View
    private lateinit var tvStandbyConn: TextView

    // Views - Settings
    private lateinit var tvSettingsClock: TextView
    private lateinit var tvSettingsDeviceName: TextView
    private lateinit var tvSettingsDeviceStatus: TextView
    private lateinit var dotSettingsConn: View
    private lateinit var switchAutoConnect: SwitchCompat
    private lateinit var tvStandbyTimeoutVal: TextView
    private lateinit var switchKeepScreenOn: SwitchCompat
    private lateinit var tvBrightnessVal: TextView
    private lateinit var seekNightBrightness: SeekBar
    private lateinit var tvRamThresholdVal: TextView
    private lateinit var switchSettingsPrivacy: SwitchCompat
    private lateinit var tvNotifSourcesVal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)
        viewFlipper = findViewById(R.id.viewFlipper)

        bindViews()
        setupAdapters()
        setupListeners()
        setupNetworking()

        applyImmersiveMode()
        applyScreenFlags()
        applyBrightness(settings.nightBrightness)
        updateClockViews()
        syncSettingsUi()

        if (settings.autoConnect && settings.lastPcIp.isNotBlank()) {
            etManualIp.setText(settings.lastPcIp)
            wsClient.connect(settings.lastPcIp, settings.lastWsPort)
            showScreen(SCREEN_DASHBOARD)
        } else {
            showScreen(SCREEN_PAIRING)
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        applyScreenFlags()
        udpClient.start()
        mainHandler.post(clockRunnable)
        resetStandbyTimer()
    }

    override fun onPause() {
        super.onPause()
        udpClient.stop()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(standbyIdleRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        udpClient.stop()
        wsClient.disconnect()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetStandbyTimer()
    }

    override fun onBackPressed() {
        if (viewFlipper.displayedChild != SCREEN_DASHBOARD && viewFlipper.displayedChild != SCREEN_PAIRING) {
            showScreen(SCREEN_DASHBOARD)
        }
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    private fun applyScreenFlags() {
        val keepOn = settings.keepScreenOn && isCharging()
        if (keepOn || settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun applyBrightness(percent: Int) {
        val lp = window.attributes
        lp.screenBrightness = (percent.coerceIn(10, 100) / 100f)
        window.attributes = lp
    }

    private fun resetStandbyTimer() {
        mainHandler.removeCallbacks(standbyIdleRunnable)
        val timeoutMin = settings.standbyTimeoutMin
        if (timeoutMin > 0 && viewFlipper.displayedChild == SCREEN_DASHBOARD) {
            mainHandler.postDelayed(standbyIdleRunnable, timeoutMin * 60_000L)
        }
    }

    private fun showScreen(index: Int) {
        viewFlipper.displayedChild = index
        resetStandbyTimer()
    }

    private fun bindViews() {
        // Pairing
        tvPairingClock = findViewById(R.id.tvPairingClock)
        pillPairingMail = findViewById(R.id.pillPairingMail)
        pillPairingWhatsapp = findViewById(R.id.pillPairingWhatsapp)
        tvDiscoveredName = findViewById(R.id.tvDiscoveredName)
        tvDiscoveredIp = findViewById(R.id.tvDiscoveredIp)
        btnConnectPc = findViewById(R.id.btnConnectPc)
        etManualIp = findViewById(R.id.etManualIp)

        // Dashboard
        tvDashClock = findViewById(R.id.tvDashClock)
        pillDashMail = findViewById(R.id.pillDashMail)
        pillDashWhatsapp = findViewById(R.id.pillDashWhatsapp)
        dotDashConnection = findViewById(R.id.dotDashConnection)
        tvDashConnection = findViewById(R.id.tvDashConnection)
        tvDashCpu = findViewById(R.id.tvDashCpu)
        tvDashRam = findViewById(R.id.tvDashRam)
        ivDashAlbumArt = findViewById(R.id.ivDashAlbumArt)
        tvDashMediaTitle = findViewById(R.id.tvDashMediaTitle)
        tvDashMediaArtist = findViewById(R.id.tvDashMediaArtist)
        btnDashPlayPause = findViewById(R.id.btnDashPlayPause)
        tvDashAudioOutput = findViewById(R.id.tvDashAudioOutput)
        tvDashClip1 = findViewById(R.id.tvDashClip1)
        tvDashPrompt1 = findViewById(R.id.tvDashPrompt1)

        // Notifications
        tvNotifClock = findViewById(R.id.tvNotifClock)
        switchNotifPrivacy = findViewById(R.id.switchNotifPrivacy)

        // System Monitor
        tvSysClock = findViewById(R.id.tvSysClock)
        pillSysRamWarning = findViewById(R.id.pillSysRamWarning)
        tvSysRamWarning = findViewById(R.id.tvSysRamWarning)
        tvSysStatusLabel = findViewById(R.id.tvSysStatusLabel)
        gaugeCpu = findViewById(R.id.gaugeCpu)
        gaugeRam = findViewById(R.id.gaugeRam)
        tvSysCpuBig = findViewById(R.id.tvSysCpuBig)
        tvSysCpuSub = findViewById(R.id.tvSysCpuSub)
        tvSysRamBig = findViewById(R.id.tvSysRamBig)
        tvSysRamUsedTotal = findViewById(R.id.tvSysRamUsedTotal)
        tvSysRamAvail = findViewById(R.id.tvSysRamAvail)

        // Prompts Vault
        tvVaultClock = findViewById(R.id.tvVaultClock)
        tvVaultTelemetry = findViewById(R.id.tvVaultTelemetry)
        chipAll = findViewById(R.id.chipAll)
        chipCodex = findViewById(R.id.chipCodex)
        chipAntigravity = findViewById(R.id.chipAntigravity)
        chipAds = findViewById(R.id.chipAds)

        // StandBy
        tvStandbyClock = findViewById(R.id.tvStandbyClock)
        tvStandbyDate = findViewById(R.id.tvStandbyDate)
        tvStandbyWeather = findViewById(R.id.tvStandbyWeather)
        ivStandbyAlbumArt = findViewById(R.id.ivStandbyAlbumArt)
        tvStandbyMediaTitle = findViewById(R.id.tvStandbyMediaTitle)
        tvStandbyMediaArtist = findViewById(R.id.tvStandbyMediaArtist)
        pbStandbyMedia = findViewById(R.id.pbStandbyMedia)
        btnStandbyPlayPause = findViewById(R.id.btnStandbyPlayPause)
        pillStandbyNotifications = findViewById(R.id.pillStandbyNotifications)
        dotStandbyConn = findViewById(R.id.dotStandbyConn)
        tvStandbyConn = findViewById(R.id.tvStandbyConn)

        // Settings
        tvSettingsClock = findViewById(R.id.tvSettingsClock)
        tvSettingsDeviceName = findViewById(R.id.tvSettingsDeviceName)
        tvSettingsDeviceStatus = findViewById(R.id.tvSettingsDeviceStatus)
        dotSettingsConn = findViewById(R.id.dotSettingsConn)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)
        tvStandbyTimeoutVal = findViewById(R.id.tvStandbyTimeoutVal)
        switchKeepScreenOn = findViewById(R.id.switchKeepScreenOn)
        tvBrightnessVal = findViewById(R.id.tvBrightnessVal)
        seekNightBrightness = findViewById(R.id.seekNightBrightness)
        tvRamThresholdVal = findViewById(R.id.tvRamThresholdVal)
        switchSettingsPrivacy = findViewById(R.id.switchSettingsPrivacy)
        tvNotifSourcesVal = findViewById(R.id.tvNotifSourcesVal)
    }

    private fun setupAdapters() {
        audioAdapter = AudioSessionAdapter { session, vol, mute ->
            wsClient.sendSetAppVolume(session.sessionId, vol, mute)
        }
        findViewById<RecyclerView>(R.id.rvAudioSessions).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = audioAdapter
        }

        whatsappAdapter = NotificationAdapter()
        findViewById<RecyclerView>(R.id.rvWhatsappNotifications).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = whatsappAdapter
        }

        mailAdapter = NotificationAdapter()
        findViewById<RecyclerView>(R.id.rvMailNotifications).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = mailAdapter
        }

        processAdapter = ProcessAdapter { proc ->
            wsClient.sendKillProcess(proc.pid)
            Toast.makeText(this, "${proc.name} kapatılıyor...", Toast.LENGTH_SHORT).show()
        }
        findViewById<RecyclerView>(R.id.rvTopProcesses).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = processAdapter
        }

        clipboardAdapter = ClipboardAdapter { clip ->
            wsClient.sendPasteText(clip.text)
            Toast.makeText(this, "PC'ye yapıştırıldı", Toast.LENGTH_SHORT).show()
        }
        findViewById<RecyclerView>(R.id.rvClipboardHistory).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = clipboardAdapter
        }

        promptAdapter = PromptAdapter { prompt ->
            wsClient.sendPasteText(prompt.text)
            Toast.makeText(this, "${prompt.title} yapıştırıldı", Toast.LENGTH_SHORT).show()
        }
        findViewById<RecyclerView>(R.id.rvPromptsGrid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = promptAdapter
        }
    }

    private fun setupListeners() {
        // Pairing
        findViewById<TextView>(R.id.tvPairingGoDashboard).setOnClickListener { showScreen(SCREEN_DASHBOARD) }
        findViewById<ImageView>(R.id.btnPairingSettings).setOnClickListener { showScreen(SCREEN_SETTINGS) }
        pillPairingMail.setOnClickListener { showScreen(SCREEN_NOTIFICATIONS) }
        pillPairingWhatsapp.setOnClickListener { showScreen(SCREEN_NOTIFICATIONS) }

        btnConnectPc.setOnClickListener {
            val target = discoveredPc
            if (target != null) {
                settings.lastPcIp = target.ipAddress
                settings.lastPcName = target.deviceName
                settings.lastWsPort = target.wsPort
                wsClient.connect(target.ipAddress, target.wsPort)
                showScreen(SCREEN_DASHBOARD)
            } else {
                val ip = etManualIp.text.toString().trim()
                if (ip.isNotEmpty()) {
                    settings.lastPcIp = ip
                    wsClient.connect(ip, 8765)
                    showScreen(SCREEN_DASHBOARD)
                } else {
                    Toast.makeText(this, "PC bulunamadı, IP girin", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<TextView>(R.id.btnManualConnect).setOnClickListener {
            val ip = etManualIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                settings.lastPcIp = ip
                wsClient.connect(ip, 8765)
                showScreen(SCREEN_DASHBOARD)
            }
        }

        // Dashboard Navigation
        pillDashMail.setOnClickListener { showScreen(SCREEN_NOTIFICATIONS) }
        pillDashWhatsapp.setOnClickListener { showScreen(SCREEN_NOTIFICATIONS) }
        findViewById<LinearLayout>(R.id.pillDashConnection).setOnClickListener { showScreen(SCREEN_PAIRING) }
        findViewById<LinearLayout>(R.id.btnDashTelemetry).setOnClickListener { showScreen(SCREEN_SYSTEM_MONITOR) }
        findViewById<ImageView>(R.id.btnDashStandby).setOnClickListener { showScreen(SCREEN_STANDBY) }
        findViewById<ImageView>(R.id.btnDashSettings).setOnClickListener { showScreen(SCREEN_SETTINGS) }
        findViewById<LinearLayout>(R.id.btnDashExpandVault).setOnClickListener { showScreen(SCREEN_PROMPTS_VAULT) }

        // Swipe left on Dashboard -> StandBy
        var downX = 0f
        findViewById<View>(R.id.dashboardRoot).setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downX = event.x
                MotionEvent.ACTION_UP -> {
                    if (downX - event.x > 160f) {
                        showScreen(SCREEN_STANDBY)
                    }
                }
            }
            false
        }

        // Dashboard Media & Audio
        findViewById<ImageView>(R.id.btnDashPrev).setOnClickListener { wsClient.sendMediaCmd("prev") }
        btnDashPlayPause.setOnClickListener { wsClient.sendMediaCmd("play_pause") }
        findViewById<ImageView>(R.id.btnDashNext).setOnClickListener { wsClient.sendMediaCmd("next") }
        findViewById<LinearLayout>(R.id.btnDashAudioOutput).setOnClickListener { wsClient.sendToggleAudioOutput() }

        // Dashboard Quick Actions
        findViewById<TextView>(R.id.btnActionCodex).setOnClickListener { wsClient.sendAction("codex") }
        findViewById<TextView>(R.id.btnActionAntigravity).setOnClickListener { wsClient.sendAction("antigravity") }
        findViewById<TextView>(R.id.btnActionMetaAds).setOnClickListener { wsClient.sendAction("meta_ads") }
        findViewById<TextView>(R.id.btnActionPremiere).setOnClickListener { wsClient.sendAction("premiere") }
        findViewById<TextView>(R.id.btnActionScreenshot).setOnClickListener { wsClient.sendAction("screenshot") }
        findViewById<TextView>(R.id.btnActionAudioSwitch).setOnClickListener { wsClient.sendToggleAudioOutput() }

        // Dashboard Preview Paste
        findViewById<LinearLayout>(R.id.rowDashClip1).setOnClickListener {
            val firstClip = currentState.clipboardHistory.firstOrNull()
            if (firstClip != null) {
                wsClient.sendPasteText(firstClip.text)
                Toast.makeText(this, "Pano yapıştırıldı", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<LinearLayout>(R.id.rowDashPrompt1).setOnClickListener {
            val firstPrompt = currentState.prompts.firstOrNull()
            if (firstPrompt != null) {
                wsClient.sendPasteText(firstPrompt.text)
                Toast.makeText(this, "${firstPrompt.title} yapıştırıldı", Toast.LENGTH_SHORT).show()
            }
        }

        // Notifications
        findViewById<ImageView>(R.id.btnNotifBack).setOnClickListener { showScreen(SCREEN_DASHBOARD) }
        switchNotifPrivacy.setOnCheckedChangeListener { _, isChecked ->
            settings.privacyMode = isChecked
            switchSettingsPrivacy.isChecked = isChecked
            renderNotifications()
        }
        findViewById<TextView>(R.id.btnClearNotifications).setOnClickListener {
            wsClient.sendClearNotifications()
        }

        // System Monitor
        findViewById<ImageView>(R.id.btnSysMonBack).setOnClickListener { showScreen(SCREEN_DASHBOARD) }

        // Prompts Vault
        findViewById<ImageView>(R.id.btnVaultBack).setOnClickListener { showScreen(SCREEN_DASHBOARD) }
        chipAll.setOnClickListener { selectPromptFilter("Tümü") }
        chipCodex.setOnClickListener { selectPromptFilter("Codex") }
        chipAntigravity.setOnClickListener { selectPromptFilter("Antigravity") }
        chipAds.setOnClickListener { selectPromptFilter("Reklam") }

        // StandBy
        findViewById<View>(R.id.standbyRoot).setOnClickListener { showScreen(SCREEN_DASHBOARD) }
        findViewById<ImageView>(R.id.btnStandbyPrev).setOnClickListener { wsClient.sendMediaCmd("prev") }
        btnStandbyPlayPause.setOnClickListener { wsClient.sendMediaCmd("play_pause") }
        findViewById<ImageView>(R.id.btnStandbyNext).setOnClickListener { wsClient.sendMediaCmd("next") }

        // Settings
        findViewById<ImageView>(R.id.btnSettingsBack).setOnClickListener { showScreen(SCREEN_DASHBOARD) }
        findViewById<LinearLayout>(R.id.rowSettingsDevice).setOnClickListener { showScreen(SCREEN_PAIRING) }
        switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            settings.autoConnect = isChecked
            wsClient.setAutoReconnect(isChecked)
        }
        findViewById<LinearLayout>(R.id.rowStandbyTimeout).setOnClickListener { showStandbyTimeoutDialog() }
        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settings.keepScreenOn = isChecked
            applyScreenFlags()
        }
        seekNightBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(10)
                tvBrightnessVal.text = "$clamped%"
                if (fromUser) {
                    settings.nightBrightness = clamped
                    applyBrightness(clamped)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        findViewById<LinearLayout>(R.id.rowRamThreshold).setOnClickListener { showRamThresholdDialog() }
        switchSettingsPrivacy.setOnCheckedChangeListener { _, isChecked ->
            settings.privacyMode = isChecked
            switchNotifPrivacy.isChecked = isChecked
            renderNotifications()
        }
        findViewById<LinearLayout>(R.id.rowNotifSources).setOnClickListener { showNotifSourcesDialog() }
        findViewById<LinearLayout>(R.id.rowDefaultLauncher).setOnClickListener { openLauncherSettings() }
        findViewById<LinearLayout>(R.id.rowSyncPc).setOnClickListener {
            wsClient.sendSyncConfig(settings.ramThreshold)
            Toast.makeText(this, "PC ile senkronize edildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNetworking() {
        udpClient = UdpDiscoveryClient(5005) { pc ->
            discoveredPc = pc
            tvDiscoveredName.text = pc.deviceName
            tvDiscoveredIp.text = "${getString(R.string.found_on_lan)} • ${pc.ipAddress}"
            if (etManualIp.text.isNullOrBlank()) {
                etManualIp.setText(pc.ipAddress)
            }
            if (settings.autoConnect && !wsClient.isConnected() && settings.lastPcIp.isBlank()) {
                settings.lastPcIp = pc.ipAddress
                settings.lastPcName = pc.deviceName
                settings.lastWsPort = pc.wsPort
                wsClient.connect(pc.ipAddress, pc.wsPort)
                showScreen(SCREEN_DASHBOARD)
            }
        }

        wsClient = DockPadWebSocketClient(
            onConnectionChanged = { connected, ip ->
                updateConnectionIndicators(connected, ip)
            },
            onStateReceived = { state ->
                currentState = state
                renderState(state)
            }
        )
        wsClient.setAutoReconnect(settings.autoConnect)
    }

    private fun updateConnectionIndicators(connected: Boolean, ip: String) {
        val dotRes = if (connected) R.drawable.dot_green else R.drawable.dot_red
        dotDashConnection.setBackgroundResource(dotRes)
        dotStandbyConn.setBackgroundResource(dotRes)
        dotSettingsConn.setBackgroundResource(dotRes)

        val pcName = currentState.deviceName.ifBlank { settings.lastPcName }
        tvDashConnection.text = if (connected) "$pcName Bağlı" else "Bağlantı Kesildi"
        tvStandbyConn.text = if (connected) "Abra A7 Bağlı" else "Bağlantı Kesildi"
        tvSettingsDeviceName.text = pcName
        tvSettingsDeviceStatus.text = if (connected) "Bağlı • $ip" else "Bağlı değil"
    }

    private fun renderState(state: PcStateUpdate) {
        // Top telemetry
        tvDashCpu.text = "CPU ${state.cpu}%"
        tvDashRam.text = "RAM ${state.ram}%"
        tvDashRam.setTextColor(
            if (state.highRamAlert) Color.parseColor("#FF453A") else Color.parseColor("#F4F4F5")
        )
        tvVaultTelemetry.text = "CPU ${state.cpu}%  RAM ${state.ram}%"

        // Media
        tvDashMediaTitle.text = state.media.title
        tvDashMediaArtist.text = state.media.artist
        tvStandbyMediaTitle.text = state.media.title
        tvStandbyMediaArtist.text = state.media.artist

        val playIcon = if (state.media.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnDashPlayPause.setImageResource(playIcon)
        btnStandbyPlayPause.setImageResource(playIcon)

        if (state.media.durationSec > 0) {
            val pct = ((state.media.positionSec * 100f) / state.media.durationSec).toInt().coerceIn(0, 100)
            pbStandbyMedia.progress = pct
        } else {
            pbStandbyMedia.progress = 0
        }

        if (state.media.coverBase64.isNotBlank()) {
            val hash = state.media.coverBase64.hashCode()
            if (hash != lastCoverHash) {
                lastCoverHash = hash
                try {
                    val bytes = Base64.decode(state.media.coverBase64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        ivDashAlbumArt.setImageBitmap(bmp)
                        ivStandbyAlbumArt.setImageBitmap(bmp)
                    }
                } catch (_: Exception) {
                }
            }
        } else {
            lastCoverHash = 0
            ivDashAlbumArt.setImageResource(R.drawable.ic_headphones)
            ivStandbyAlbumArt.setImageResource(R.drawable.ic_headphones)
        }

        // Audio mixer
        tvDashAudioOutput.text = state.audioOutput
        audioAdapter.submitList(state.audioSessions)

        // Clipboard & Prompts
        tvDashClip1.text = state.clipboardHistory.firstOrNull()?.preview ?: "Pano boş"
        tvDashPrompt1.text = state.prompts.firstOrNull()?.title ?: "Prompt yok"
        clipboardAdapter.submitList(state.clipboardHistory)
        renderFilteredPrompts()

        // System Monitor
        val blue = Color.parseColor("#0381FE")
        val red = Color.parseColor("#FF453A")
        gaugeCpu.setProgress(state.cpu, blue)
        gaugeRam.setProgress(state.ram, if (state.highRamAlert) red else blue)
        tvSysCpuBig.text = "${state.cpu}%"
        tvSysCpuSub.text = "${state.cpuGhz} GHz • ${state.cpuCores} Çekirdek"
        tvSysRamBig.text = "${state.ram}%"
        tvSysRamUsedTotal.text = "${state.ramUsedGb} / ${state.ramTotalGb} GB"
        tvSysRamAvail.text = "Kullanılabilir: ${state.ramAvailableGb} GB"

        if (state.highRamAlert) {
            pillSysRamWarning.visibility = View.VISIBLE
            tvSysRamWarning.text = "RAM %${state.ram}"
            tvSysStatusLabel.text = "Yüksek"
        } else {
            pillSysRamWarning.visibility = View.GONE
            tvSysStatusLabel.text = "Normal"
        }
        processAdapter.submitList(state.topProcesses)

        // Weather
        tvStandbyWeather.text = "${state.weather.city} • ${state.weather.tempC}° ${state.weather.condition}"

        // Notifications
        renderNotifications()
    }

    private fun renderNotifications() {
        val waList = if (settings.notifWhatsappEnabled) {
            currentState.notifications.filter { it.source == "WhatsApp" }
        } else emptyList()

        val mailList = if (settings.notifMailEnabled) {
            currentState.notifications.filter { it.source == "Mail" }
        } else emptyList()

        whatsappAdapter.submitList(waList, settings.privacyMode)
        mailAdapter.submitList(mailList, settings.privacyMode)

        val waText = "WhatsApp (${waList.size})"
        val mailText = "Mail (${mailList.size})"
        pillDashWhatsapp.text = waText
        pillDashMail.text = mailText
        pillPairingWhatsapp.text = waText
        pillPairingMail.text = mailText
        pillStandbyNotifications.text = "$waText • $mailText"
    }

    private fun selectPromptFilter(category: String) {
        activePromptFilter = category
        val chips = listOf(
            chipAll to "Tümü",
            chipCodex to "Codex",
            chipAntigravity to "Antigravity",
            chipAds to "Reklam"
        )
        for ((view, label) in chips) {
            val selected = label == category
            view.setBackgroundResource(if (selected) R.drawable.bg_pill_blue else R.drawable.bg_pill_dark)
            view.setTextColor(Color.parseColor(if (selected) "#FFFFFF" else "#A1A1AA"))
        }
        renderFilteredPrompts()
    }

    private fun renderFilteredPrompts() {
        val filtered = if (activePromptFilter == "Tümü") {
            currentState.prompts
        } else {
            currentState.prompts.filter { it.category.equals(activePromptFilter, ignoreCase = true) }
        }
        promptAdapter.submitList(filtered)
    }

    private fun updateClockViews() {
        val now = Date()
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val dateStr = SimpleDateFormat("d MMMM EEEE", Locale("tr", "TR")).format(now)

        tvPairingClock.text = timeStr
        tvDashClock.text = timeStr
        tvNotifClock.text = timeStr
        tvSysClock.text = timeStr
        tvVaultClock.text = timeStr
        tvStandbyClock.text = timeStr
        tvSettingsClock.text = timeStr
        tvStandbyDate.text = dateStr
    }

    private fun syncSettingsUi() {
        switchAutoConnect.isChecked = settings.autoConnect
        switchKeepScreenOn.isChecked = settings.keepScreenOn
        seekNightBrightness.progress = settings.nightBrightness
        tvBrightnessVal.text = "${settings.nightBrightness}%"
        tvRamThresholdVal.text = "%${settings.ramThreshold}"
        switchNotifPrivacy.isChecked = settings.privacyMode
        switchSettingsPrivacy.isChecked = settings.privacyMode
        tvStandbyTimeoutVal.text = if (settings.standbyTimeoutMin == 0) "Kapalı" else "${settings.standbyTimeoutMin} Dakika"
        updateNotifSourcesLabel()
    }

    private fun updateNotifSourcesLabel() {
        val parts = mutableListOf<String>()
        if (settings.notifWhatsappEnabled) parts.add("WhatsApp")
        if (settings.notifMailEnabled) parts.add("Mail")
        tvNotifSourcesVal.text = if (parts.isEmpty()) "Kapalı" else parts.joinToString(", ")
    }

    private fun showStandbyTimeoutDialog() {
        val labels = arrayOf("3 Dakika", "5 Dakika", "10 Dakika", "Kapalı")
        val values = intArrayOf(3, 5, 10, 0)
        AlertDialog.Builder(this)
            .setTitle("StandBy Süresi")
            .setItems(labels) { _, which ->
                settings.standbyTimeoutMin = values[which]
                tvStandbyTimeoutVal.text = labels[which]
                resetStandbyTimer()
            }
            .show()
    }

    private fun showRamThresholdDialog() {
        val labels = arrayOf("%75", "%80", "%85", "%90", "%95")
        val values = intArrayOf(75, 80, 85, 90, 95)
        AlertDialog.Builder(this)
            .setTitle("RAM Uyarı Eşiği")
            .setItems(labels) { _, which ->
                settings.ramThreshold = values[which]
                tvRamThresholdVal.text = labels[which]
                wsClient.sendSyncConfig(values[which])
            }
            .show()
    }

    private fun showNotifSourcesDialog() {
        val items = arrayOf("WhatsApp", "Mail")
        val checked = booleanArrayOf(settings.notifWhatsappEnabled, settings.notifMailEnabled)
        AlertDialog.Builder(this)
            .setTitle("Bildirim Kaynakları")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Tamam") { _, _ ->
                settings.notifWhatsappEnabled = checked[0]
                settings.notifMailEnabled = checked[1]
                updateNotifSourcesLabel()
                renderNotifications()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun openLauncherSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(Intent.createChooser(intent, "Varsayılan Ana Ekran Seç"))
        }
    }
}
