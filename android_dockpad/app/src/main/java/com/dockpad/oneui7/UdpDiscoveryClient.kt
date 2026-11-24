package com.dockpad.oneui7

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

class UdpDiscoveryClient(
    private val port: Int = 5005,
    private val onPcDiscovered: (DiscoveredPc) -> Unit
) {
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var socket: DatagramSocket? = null
    private var workerThread: Thread? = null

    fun start() {
        if (running.getAndSet(true)) return
        workerThread = Thread({
            try {
                val sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 2000
                    bind(InetSocketAddress(port))
                }
                socket = sock
                val buffer = ByteArray(2048)

                while (running.get()) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        sock.receive(packet)
                        val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(jsonStr)
                        if (json.optString("service") == "dockpad_pc_agent") {
                            val name = json.optString("device_name", "Monster Abra A7")
                            val wsPort = json.optInt("ws_port", 8765)
                            val ip = packet.address.hostAddress ?: continue
                            val pc = DiscoveredPc(name, ip, wsPort)
                            mainHandler.post { onPcDiscovered(pc) }
                        }
                    } catch (_: Exception) {
                        // Timeout or malformed packet; continue listening
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                socket = null
            }
        }, "UdpDiscoveryThread").apply { start() }
    }

    fun stop() {
        running.set(false)
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        workerThread = null
    }
}
