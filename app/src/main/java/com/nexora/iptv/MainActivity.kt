package com.nexora.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nexora.iptv.data.Channel
import com.nexora.iptv.data.EpgProgram
import com.nexora.iptv.data.IptvCatalog
import com.nexora.iptv.data.IptvRepository
import com.nexora.iptv.data.MediaType
import kotlinx.coroutines.launch

private val Night = Color(0xFF080D18)
private val Panel = Color(0xFF121D2D)
private val Mint = Color(0xFF55D6BE)
private val Muted = Color(0xFF9AA9BF)

private fun favoriteKey(channel: Channel): String =
    "${channel.type.name}|${channel.streamId ?: channel.tvgId ?: channel.name}"

class MainActivity : ComponentActivity() {
    private val repository = IptvRepository()
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint, secondary = Color(0xFF8AA8FF),
                background = Night, surface = Panel
            )) {
                NexoraApp(
                    onLoadM3u = { url, done -> lifecycleScope.launch {
                        runCatching { repository.loadM3u(url) }
                            .onSuccess { done(IptvCatalog(it, emptyList(), emptyList()), null) }
                            .onFailure { done(null, it.message ?: "Unable to load playlist") }
                    } },
                    onXtream = { server, user, pass, done -> lifecycleScope.launch {
                        runCatching { repository.loadXtream(server, user, pass) }
                            .onSuccess { done(it, null) }
                            .onFailure { done(null, it.message ?: "Connection failed") }
                    } },
                    onSeries = { server, user, pass, id, done -> lifecycleScope.launch {
                        runCatching { repository.loadSeriesEpisodes(server, user, pass, id) }
                            .onSuccess { done(it, null) }
                            .onFailure { done(emptyList(), it.message ?: "Unable to load episodes") }
                    } },
                    onEpg = { server, user, pass, id, done -> lifecycleScope.launch {
                        runCatching { repository.loadShortEpg(server, user, pass, id) }
                            .onSuccess { done(it, null) }
                            .onFailure { done(emptyList(), it.message ?: "EPG unavailable") }
                    } },
                    onPlay = { channel -> player?.apply {
                        setMediaItem(MediaItem.fromUri(channel.url))
                        prepare()
                        playWhenReady = true
                    } },
                    playerView = { AndroidView(factory = { context ->
                        PlayerView(context).apply {
                            player = this@MainActivity.player
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    }, modifier = Modifier.fillMaxWidth().height(250.dp)) }
                )
            }
        }
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}

@Composable
private fun NexoraApp(
    onLoadM3u: (String, (IptvCatalog?, String?) -> Unit) -> Unit,
    onXtream: (String, String, String, (IptvCatalog?, String?) -> Unit) -> Unit,
    onSeries: (String, String, String, String, (List<Channel>, String?) -> Unit) -> Unit,
    onEpg: (String, String, String, String, (List<EpgProgram>, String?) -> Unit) -> Unit,
    onPlay: (Channel) -> Unit,
    playerView: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("nexora_preferences", 0) }
    var mode by remember { mutableIntStateOf(0) }
    var sourceUrl by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf(IptvCatalog(emptyList(), emptyList(), emptyList())) }
    var episodes by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var showingEpisodes by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var epg by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var epgTitle by remember { mutableStateOf("") }
    var showEpg by remember { mutableStateOf(false) }

    val currentList = when (tab) {
        0 -> catalog.live
        1 -> catalog.movies
        2 -> catalog.series
        else -> (catalog.live + catalog.movies + catalog.series).filter { favorites.contains(favoriteKey(it)) }
    }
    val filtered = currentList.filter {
        (it.name.contains(query, true) || it.group.contains(query, true)) &&
            (category == "All" || it.group == category)
    }
    val categories = listOf("All") + currentList.map { it.group }.distinct().sorted()
    val total = catalog.live.size + catalog.movies.size + catalog.series.size

    Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NEXORA", color = Color.White, fontSize = 27.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("ENTERTAINMENT PLAYER", color = Mint, fontSize = 10.sp, letterSpacing = 2.5.sp)
            }
            Surface(color = Color(0xFF14332F), shape = RoundedCornerShape(30.dp)) {
                Text("ANDROID", color = Mint, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp)
            }
        }

        if (selected != null) {
            val item = selected!!
            Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            playerView()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = null }) { Text("‹  Library") }
                Spacer(Modifier.weight(1f))
                if (item.type == MediaType.LIVE && item.streamId != null && mode == 1) {
                    TextButton(onClick = {
                        onEpg(server, username, password, item.streamId) { list, error ->
                            epg = list
                            epgTitle = item.name
                            message = error
                            showEpg = true
                        }
                    }) { Text("PROGRAM GUIDE") }
                }
            }
            Text("Playback controls include audio and subtitle tracks when supplied by your provider.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        } else {
            Text("One player. Your authorized content sources.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("M3U / M3U8") })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Xtream Codes") })
            }
            if (mode == 0) {
                OutlinedTextField(
                    value = sourceUrl, onValueChange = { sourceUrl = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Playlist URL") },
                    placeholder = { Text("https://example.com/list.m3u") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            } else {
                OutlinedTextField(server, { server = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(username, { username = it }, modifier = Modifier.weight(1f),
                        label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, modifier = Modifier.weight(1f),
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
            }
            Button(
                onClick = {
                    busy = true
                    message = null
                    showingEpisodes = false
                    val done: (IptvCatalog?, String?) -> Unit = { result, error ->
                        if (result != null) {
                            catalog = result
                            tab = if (result.live.isNotEmpty()) 0 else if (result.movies.isNotEmpty()) 1 else 2
                            category = "All"
                            query = ""
                        }
                        message = error
                        busy = false
                    }
                    if (mode == 0) onLoadM3u(sourceUrl.trim(), done)
                    else onXtream(server.trim(), username.trim(), password, done)
                },
                enabled = !busy && if (mode == 0) sourceUrl.isNotBlank()
                    else server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                else Text(if (total == 0) "CONNECT SOURCE" else "REFRESH CONTENT")
            }
            message?.let { Text(it, color = Color(0xFFFF9292), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

            if (total > 0) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$total ITEMS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Live ${catalog.live.size}  •  Movies ${catalog.movies.size}  •  Series ${catalog.series.size}",
                        color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                val tabs = listOf("LIVE", "MOVIES", "SERIES", "FAVORITES")
                ScrollableTabRow(selectedTabIndex = tab, containerColor = Night, contentColor = Mint,
                    edgePadding = 0.dp, divider = {}) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = {
                            tab = index
                            category = "All"
                            query = ""
                        }, text = { Text(label, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Search title or category") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }
                )
                if (categories.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 7.dp)) {
                        items(categories) { name ->
                            FilterChip(selected = category == name, onClick = { category = name },
                                label = { Text(name, maxLines = 1, fontSize = 10.sp) })
                        }
                    }
                }
                if (showingEpisodes) {
                    TextButton(onClick = { showingEpisodes = false }) { Text("‹ Back to series") }
                }
                val displayList = if (showingEpisodes) episodes else filtered
                if (displayList.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(if (tab == 3) "No favorites yet. Tap the heart on any item." else "No matching content.",
                            color = Muted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(displayList, key = { it.url + it.name + it.streamId }) { item ->
                            val key = favoriteKey(item)
                            Surface(color = Panel, shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (item.type == MediaType.SERIES && item.streamId != null) {
                                        busy = true
                                        onSeries(server, username, password, item.streamId) { list, error ->
                                            episodes = list
                                            showingEpisodes = true
                                            message = error ?: if (list.isEmpty()) "No episodes returned for this series." else null
                                            busy = false
                                        }
                                    } else {
                                        selected = item
                                        onPlay(item)
                                    }
                                }.focusable()) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = Color(0xFF1B3540), shape = RoundedCornerShape(9.dp)) {
                                        Icon(Icons.Default.PlayArrow, null, tint = Mint,
                                            modifier = Modifier.padding(8.dp).size(22.dp))
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp, maxLines = 2)
                                        Text(item.group + if (item.type == MediaType.SERIES) "  •  Open episodes" else "",
                                            color = Muted, fontSize = 10.sp, maxLines = 1)
                                    }
                                    IconButton(onClick = {
                                        favorites = (if (favorites.contains(key)) favorites - key else favorites + key).also { preferences.edit().putStringSet("favorites", it).apply() }
                                    }) {
                                        Icon(if (favorites.contains(key)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (favorites.contains(key)) Color(0xFFFF6D91) else Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text("Connect your own playlist or provider account to load live TV, movies and series.",
                    color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("No channels or subscriptions are included with NEXORA.",
                    color = Color(0xFF718198), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (showEpg) {
        AlertDialog(
            onDismissRequest = { showEpg = false },
            title = { Text("Program guide · $epgTitle") },
            text = {
                if (epg.isEmpty()) Text(message ?: "No guide information available.")
                else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    epg.forEach { program ->
                        Text(program.title, color = Mint, fontWeight = FontWeight.Bold)
                        Text(listOf(program.start, program.end).filter { it.isNotBlank() }.joinToString(" – "),
                            color = Muted, fontSize = 11.sp)
                        if (program.description.isNotBlank()) Text(program.description, fontSize = 12.sp)
                        HorizontalDivider(Modifier.padding(vertical = 7.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEpg = false }) { Text("CLOSE") } }
        )
    }
}
