package id.photobooth.app

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    /** Alamat IP tablet di jaringan Wi-Fi / hotspot. */
    fun localIp(): String? {
        val candidates = mutableListOf<Pair<String, String>>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        candidates += ni.name to (addr.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        val preferred = candidates.firstOrNull { (name, _) ->
            name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap")
        }
        return (preferred ?: candidates.firstOrNull())?.second
    }
}
