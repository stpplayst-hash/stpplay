package com.stpplay.android.ui

import android.content.Context
import android.provider.Settings
import java.net.NetworkInterface
import java.util.*

fun getMacAddress(context: Context): String {
    try {
        val allInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        // Ordem de preferência: Ethernet -> Wi-Fi -> Outros
        val targetNames = listOf("eth0", "eth1", "wlan0", "wlan1", "p2p0", "en0", "en1")
        
        for (name in targetNames) {
            val nif = allInterfaces.find { it.name.equals(name, ignoreCase = true) }
            val mac = nif?.hardwareAddress
            if (mac != null) {
                val res = mac.joinToString(":") { String.format("%02X", it) }
                if (res != "02:00:00:00:00:00" && res != "00:00:00:00:00:00") return res
            }
        }
    } catch (e: Exception) { }
    
    // Virtual MAC Fallback: Gera um MAC baseado no Android ID único
    return try {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "STPPLAY"
        val hash = androidId.hashCode().toLong()
        val random = Random(hash)
        val macBytes = ByteArray(6)
        random.nextBytes(macBytes)
        // Forçar o bit localmente administrado
        macBytes[0] = (macBytes[0].toInt() or 0x02).toByte()
        macBytes.joinToString(":") { String.format("%02X", it) }
    } catch (e: Exception) {
        "00:11:22:33:44:55"
    }
}
