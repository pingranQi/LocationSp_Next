package com.vincenthzr.locationspoofer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WigleClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private companion object { const val MAX_RESPONSE_BYTES = 1_048_576L }

    private fun readBoundedBody(response: okhttp3.Response): String? {
        val body = response.body ?: return null
        if (body.contentLength() > MAX_RESPONSE_BYTES) return null
        val text = body.string()
        return text.takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES }
    }

    /** 验证 token 是否有效（调用 /profile/user 接口） */
    suspend fun validateToken(token: String): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        return@withContext try {
            val request = Request.Builder()
                .url("https://api.wigle.net/api/v2/profile/user")
                .addHeader("Authorization", "Basic $token")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = readBoundedBody(response) ?: return@use false
                org.json.JSONObject(body).optString("success") == "true"
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchWifiData(lat: Double, lng: Double, token: String): String =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) {
                return@withContext generateFallbackWifi()
            }

            val latrange1 = lat - 0.002
            val latrange2 = lat + 0.002
            val longrange1 = lng - 0.002
            val longrange2 = lng + 0.002

            val url =
                "https://api.wigle.net/api/v2/network/search?latrange1=$latrange1&latrange2=$latrange2&longrange1=$longrange1&longrange2=$longrange2"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic $token")
                .addHeader("Accept", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext generateFallbackWifi()
                    val body = readBoundedBody(response) ?: return@withContext generateFallbackWifi()
                    val results = JSONObject(body).optJSONArray("results")
                        ?: return@withContext generateFallbackWifi()
                    if (results.length() == 0) return@withContext generateFallbackWifi()
                    val wifiList = mutableListOf<JSONObject>()
                    for (i in 0 until minOf(results.length(), 10)) {
                        val item = results.getJSONObject(i)
                        wifiList.add(JSONObject().apply {
                            put("ssid", item.optString("ssid", "WLAN_${(1000..9999).random()}"))
                            put("bssid", item.optString("netid", generateRandomBssid()))
                        })
                    }
                    return@withContext wifiList.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext generateFallbackWifi()
            }
        }

    /**
     * 生成符合真实OUI规范的BSSID
     *
     * IEEE OUI(Organizationally Unique Identifier)是MAC地址前3字节,由IEEE分配给设备厂商。
     * 反作弊SDK会校验OUI前缀是否属于已注册的合法厂商,纯随机MAC会因OUI不存在而被标记。
     * 以下OUI均来自中国市场主流路由器/AP厂商的真实注册前缀。
     */
    private fun generateRandomBssid(): String {
        val ouis = listOf(
            "00:14:22", // Dell
            "cc:2d:e0", // Routerboard
            "44:a8:42", // Cisco Meraki
            "00:25:9c", // Cisco-Linksys
            "60:e3:27", // TP-Link(普联)
            "b0:95:75", // TP-Link(普联)
            "10:41:7f", // TP-Link(普联)
            "88:25:93", // Tenda(腾达)
            "c8:3a:35", // Tenda(腾达)
            "48:7d:2e", // Huawei(华为)
            "e0:19:1d", // Huawei(华为)
            "34:12:f9", // Huawei(华为)
            "28:d1:27", // ZTE(中兴)
            "64:13:6c", // ZTE(中兴)
            "78:11:dc", // Xiaomi(小米)
            "50:64:2b", // Xiaomi(小米)
            "74:da:88", // Edimax
            "c0:25:e9", // Netgear
            "20:76:93", // ASUS(华硕)
            "b4:fb:e4", // D-Link
            "fc:d7:33", // H3C(新华三)
            "3c:37:86"  // Ruijie(锐捷)
        )
        return "${ouis.random()}:${
            String.format(
                java.util.Locale.US,
                "%02x:%02x:%02x",
                (0..255).random(),
                (0..255).random(),
                (0..255).random()
            )
        }"
    }

    private fun generateFallbackWifi(): String {
        val list = mutableListOf<JSONObject>()
        for (i in 0..9) {
            val wifi = JSONObject()
            wifi.put("ssid", "WLAN_${(1000..9999).random()}")
            wifi.put("bssid", generateRandomBssid())
            list.add(wifi)
        }
        return list.toString()
    }
}
