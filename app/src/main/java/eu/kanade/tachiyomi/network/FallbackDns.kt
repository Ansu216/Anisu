package eu.kanade.tachiyomi.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * The phone's own DNS first; DNS-over-HTTPS only when that fails for a site.
 *
 * Some networks and DNS filters answer a blocked domain with nothing or with an empty address (`0.0.0.0` / `::`),
 * which shows up as "Failed to connect to host/[::]:443". Extensions that depend on such a host (for example an
 * ID mapper) then list no streams although the main site loads fine. Sites that resolve normally never touch the
 * DoH path, so there is no extra delay for them.
 */
internal class FallbackDns : Dns {

    private val doh: Dns = DnsOverHttps.Builder()
        .client(OkHttpClient.Builder().build())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            listOf(
                InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
                InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1)),
            ),
        )
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val system = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            null
        }
        val blocked = system == null || system.isEmpty() ||
            (hostname != "localhost" && system.all { it.isAnyLocalAddress || it.isLoopbackAddress })
        if (!blocked) return system!!
        return try {
            doh.lookup(hostname).takeIf { it.isNotEmpty() } ?: throw UnknownHostException(hostname)
        } catch (e: Exception) {
            // DoH could not help either: report what the system said, as before.
            system?.takeIf { it.isNotEmpty() } ?: throw (e as? UnknownHostException ?: UnknownHostException(hostname))
        }
    }
}
