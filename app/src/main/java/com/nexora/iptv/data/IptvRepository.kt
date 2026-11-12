package com.nexora.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class IptvCatalog(
    val live: List<Channel>,
    val movies: List<Channel>,
    val series: List<Channel>
)

class IptvRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun loadM3u(url: String): List<Channel> = withContext(Dispatchers.IO) {
        requireHttpUrl(url)
        val request = Request.Builder().url(url).header("User-Agent", "NEXORA IPTV/1.1").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("The playlist is empty.")
            M3uParser.parse(body).also {
                if (it.isEmpty()) error("No playable HTTP(S) entries were found.")
            }
        }
    }

    suspend fun loadXtream(server: String, username: String, password: String): IptvCatalog =
        withContext(Dispatchers.IO) {
            requireHttpUrl(server)
            require(username.isNotBlank() && password.isNotBlank()) { "Enter username and password." }
            val base = server.trim().trimEnd('/')
            val u = encode(username)
            val p = encode(password)
            val auth = "$base/player_api.php?username=$u&password=$p"
            val profile = getJsonObject(auth)
            val userInfo = profile.optJSONObject("user_info")
            val authStatus = userInfo?.optString("auth")
            if (authStatus == "0") error("Provider rejected these account details.")
            val live = getArray("$auth&action=get_live_streams").toChannels(base, u, p, MediaType.LIVE)
            val movies = getArray("$auth&action=get_vod_streams").toChannels(base, u, p, MediaType.MOVIE)
            val series = getArray("$auth&action=get_series").toChannels(base, u, p, MediaType.SERIES)
            if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                error("The provider returned no content. Check the server and subscription.")
            }
            IptvCatalog(live, movies, series)
        }

    suspend fun loadSeriesEpisodes(server: String, username: String, password: String, seriesId: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val base = server.trim().trimEnd('/')
            val auth = "$base/player_api.php?username=${encode(username)}&password=${encode(password)}&action=get_series_info&series_id=${encode(seriesId)}"
            val info = getJsonObject(auth)
            val episodes = info.optJSONObject("episodes") ?: return@withContext emptyList()
            buildList {
                val keys = episodes.keys()
                while (keys.hasNext()) {
                    val season = keys.next()
                    val list = episodes.optJSONArray(season) ?: continue
                    for (i in 0 until list.length()) {
                        val item = list.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank() || id == "null") continue
                        val ext = item.optString("container_extension").ifBlank { "mp4" }
                        val title = item.optString("title").ifBlank { "Episode ${i + 1}" }
                        add(Channel(title, "$base/series/${encode(username)}/${encode(password)}/$id.$ext",
                            "Season $season", type = MediaType.MOVIE, streamId = id, extension = ext))
                    }
                }
            }
        }

    suspend fun loadShortEpg(server: String, username: String, password: String, streamId: String): List<EpgProgram> =
        withContext(Dispatchers.IO) {
            val url = "${server.trimEnd('/')}/player_api.php?username=${encode(username)}&password=${encode(password)}&action=get_short_epg&stream_id=${encode(streamId)}& limit=8"
                .replace("& limit", "&limit")
            val root = getJsonObject(url)
            val data = root.optJSONArray("epg_listings") ?: JSONArray()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    add(EpgProgram(
                        title = decodeBase64(item.optString("title")).ifBlank { "Program" },
                        description = decodeBase64(item.optString("description")),
                        start = item.optString("start"),
                        end = item.optString("end")
                    ))
                }
            }
        }

    private fun JSONArray.toChannels(base: String, user: String, pass: String, type: MediaType): List<Channel> =
        buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val id = item.optString(if (type == MediaType.SERIES) "series_id" else "stream_id")
                if (id.isBlank() || id == "null") continue
                val ext = item.optString("container_extension").ifBlank {
                    if (type == MediaType.LIVE) "ts" else "mp4"
                }
                val name = item.optString("name").ifBlank { item.optString("title") }.ifBlank { "Item $id" }
                val group = item.optString("category_name").ifBlank {
                    if (type == MediaType.LIVE) "Live TV" else if (type == MediaType.MOVIE) "Movies" else "Series"
                }
                val logo = item.optString(if (type == MediaType.LIVE) "stream_icon" else "cover")
                    .takeIf { it.startsWith("http") }
                val url = when (type) {
                    MediaType.LIVE -> "$base/live/$user/$pass/$id.$ext"
                    MediaType.MOVIE -> "$base/movie/$user/$pass/$id.$ext"
                    MediaType.SERIES -> ""
                }
                add(Channel(name, url, group, logo, item.optString("epg_channel_id").takeIf { it.isNotBlank() },
                    type, id, ext))
            }
        }

    private fun getArray(url: String): JSONArray {
        val request = Request.Builder().url(url).header("User-Agent", "NEXORA IPTV/1.1").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider request failed: HTTP ${response.code}")
            val raw = response.body?.string().orEmpty()
            return JSONArray(raw)
        }
    }

    private fun getJsonObject(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "NEXORA IPTV/1.1").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider request failed: HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun requireHttpUrl(url: String) {
        require(url.startsWith("https://", true) || url.startsWith("http://", true)) {
            "Enter a valid HTTP or HTTPS source URL."
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun decodeBase64(value: String): String = runCatching {
        String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)
    }.getOrDefault(value)
}
