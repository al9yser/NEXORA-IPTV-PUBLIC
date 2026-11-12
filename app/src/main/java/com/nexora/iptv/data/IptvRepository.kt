package com.nexora.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class IptvRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()

    suspend fun loadM3u(url: String): List<Channel> = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "For your security, use an HTTPS playlist URL." }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("The playlist is empty.")
            M3uParser.parse(body).also { if (it.isEmpty()) error("No playable HTTP(S) entries were found.") }
        }
    }

    suspend fun loginXtream(server: String, username: String, password: String): List<Channel> =
        withContext(Dispatchers.IO) {
            require(server.startsWith("https://")) { "For your security, use an HTTPS server URL." }
            require(username.isNotBlank() && password.isNotBlank()) { "Enter username and password." }
            val base = server.trimEnd('/')
            val u = URLEncoder.encode(username, Charsets.UTF_8.name())
            val p = URLEncoder.encode(password, Charsets.UTF_8.name())
            val apiUrl = "$base/player_api.php?username=$u&password=$p&action=get_live_streams"
            val request = Request.Builder().url(apiUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Provider request failed: HTTP ${response.code}")
                val json = JSONArray(response.body?.string().orEmpty())
                buildList {
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        val streamId = item.optString("stream_id")
                        if (streamId.isBlank() || streamId == "null") continue
                        val ext = item.optString("container_extension").ifBlank { "ts" }
                        val name = item.optString("name").ifBlank { "Channel $streamId" }
                        val category = item.optString("category_name").ifBlank { "Live TV" }
                        val logo = item.optString("stream_icon").takeIf { it.startsWith("https://") }
                        add(Channel(name, "$base/live/$u/$p/$streamId.$ext", category, logo))
                    }
                }.also { if (it.isEmpty()) error("No live channels returned. Check provider details and package.") }
            }
        }
}
