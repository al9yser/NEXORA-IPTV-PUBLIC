package com.nexora.iptv

import android.os.Bundle
import android.content.pm.ActivityInfo
import android.view.View
import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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

private val Canvas = Color(0xFF080B14)
private val SurfaceOne = Color(0xFF111827)
private val SurfaceTwo = Color(0xFF182235)
private val Accent = Color(0xFF66E0CB)
private val AccentDark = Color(0xFF173D3A)
private val TextPrimary = Color(0xFFF4F6FB)
private val TextSecondary = Color(0xFF9AA8BD)
private val Stroke = Color(0xFF29364A)
private val Danger = Color(0xFFFF7D8B)

private fun favoriteKey(channel: Channel): String =
    "${channel.type.name}|${channel.streamId ?: channel.tvgId ?: channel.name}"

class MainActivity : ComponentActivity() {
    private val repository = IptvRepository()
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Canvas,
                    secondary = Color(0xFF9DABFF),
                    background = Canvas,
                    surface = SurfaceOne,
                    onSurface = TextPrimary,
                    onSurfaceVariant = TextSecondary,
                    outline = Stroke
                )
            ) {
                NexoraApp(
                    onLoadM3u = { url, done -> lifecycleScope.launch {
                        runCatching { repository.loadM3u(url) }
                            .onSuccess { list ->
                                done(IptvCatalog(
                                    list.filter { it.type == MediaType.LIVE },
                                    list.filter { it.type == MediaType.MOVIE },
                                    list.filter { it.type == MediaType.SERIES }
                                ), null)
                            }
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
                        if (channel == null) pause() else {
                            setMediaItem(MediaItem.fromUri(channel.url))
                            prepare()
                            playWhenReady = true
                        }
                    } },
                    playerView = { fullscreen ->
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    player = this@MainActivity.player
                                    useController = true
                                    controllerAutoShow = true
                                    controllerShowTimeoutMs = 4500
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    setKeepContentOnPlayerReset(true)
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        )
                    },
                    onFullscreen = { enabled ->
                        requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        if (enabled) {
                            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
                        } else {
                            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        }
                    },
                    onTrackDialog = { type, title ->
                        TrackSelectionDialogBuilder(this, title, player!!, type).build().show()
                    }
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
    onPlay: (Channel?) -> Unit,
    playerView: @Composable (Boolean) -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onTrackDialog: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("nexora_preferences", 0) }

    var mode by remember { mutableIntStateOf(1) }
    var sourceUrl by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf(IptvCatalog(emptyList(), emptyList(), emptyList())) }
    var connected by remember { mutableStateOf(false) }
    var episodes by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var showingEpisodes by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var favorites by remember {
        mutableStateOf(preferences.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var epg by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var epgTitle by remember { mutableStateOf("") }
    var showEpg by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    val allItems = remember(catalog) { catalog.live + catalog.movies + catalog.series }
    val currentList = remember(catalog, tab, favorites) {
        when (tab) {
            0 -> catalog.live
            1 -> catalog.movies
            2 -> catalog.series
            else -> allItems.filter { favorites.contains(favoriteKey(it)) }
        }
    }
    val filtered = remember(currentList, query, category) {
        currentList.filter {
            (it.name.contains(query, true) || it.group.contains(query, true)) &&
                (category == "All" || it.group == category)
        }
    }
    val categories = remember(currentList) {
        listOf("All") + currentList.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val total = catalog.live.size + catalog.movies.size + catalog.series.size

    BackHandler(enabled = selected != null || showingEpisodes || connected) {
        when {
            selected != null -> { if (fullscreen) { fullscreen = false; onFullscreen(false) } else { selected = null; onPlay(null) } }
            showingEpisodes -> showingEpisodes = false
            connected -> { connected = false; message = null }
        }
    }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        when {
            selected != null -> {
                val item = selected!!
                Column(
                    Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!fullscreen) BrandHeader(
                        trailing = {
                            TextButton(onClick = { selected = null; onPlay(null) }) {
                                Text("LIBRARY", color = Accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    if (!fullscreen) Text(item.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier)) {
                        playerView(fullscreen)
                        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { onTrackDialog(C.TRACK_TYPE_VIDEO, "Video quality") }) { Text("QUALITY", color = Accent, fontSize = 10.sp) }
                                    TextButton(onClick = { onTrackDialog(C.TRACK_TYPE_AUDIO, "Audio track") }) { Text("AUDIO", color = Accent, fontSize = 10.sp) }
                                    TextButton(onClick = { onTrackDialog(C.TRACK_TYPE_TEXT, "Subtitles") }) { Text("SUBTITLES", color = Accent, fontSize = 10.sp) }
                                    TextButton(onClick = { fullscreen = !fullscreen; onFullscreen(fullscreen) }) { Text(if (fullscreen) "EXIT" else "FULL" , color = Accent, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                    if (!fullscreen) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.group, color = TextSecondary, fontSize = 13.sp)
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
                    if (!fullscreen) Text(
                        "Choose video quality, audio and subtitle tracks when the stream provides them. Fullscreen switches to landscape.",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
            connected -> {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    BrandHeader(
                        trailing = {
                            IconButton(onClick = { connected = false; message = null }) {
                                Icon(Icons.Default.Settings, contentDescription = "Source settings", tint = TextSecondary)
                            }
                        }
                    )
                    Column(Modifier.padding(top = 8.dp, bottom = 14.dp)) {
                        Text("YOUR LIBRARY", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Everything in one place.", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("$total titles available from your source", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        StatPill("LIVE", catalog.live.size.toString(), Icons.Default.LiveTv, Modifier.weight(1f))
                        StatPill("MOVIES", catalog.movies.size.toString(), Icons.Default.Movie, Modifier.weight(1f))
                        StatPill("SERIES", catalog.series.size.toString(), Icons.Default.Tv, Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceOne).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("Live", "Movies", "Series", "Saved").forEachIndexed { index, label ->
                            val count = when (index) {
                                0 -> catalog.live.size
                                1 -> catalog.movies.size
                                2 -> catalog.series.size
                                else -> favorites.size
                            }
                            LibraryTab(
                                label = label,
                                count = count,
                                selected = tab == index,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    tab = index
                                    category = "All"
                                    query = ""
                                    showingEpisodes = false
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search your library", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Stroke,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        )
                    )
                    if (categories.size > 1 && !showingEpisodes) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            categories.forEach { name ->
                                FilterChip(
                                    selected = category == name,
                                    onClick = { category = name },
                                    label = { Text(name, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentDark,
                                        selectedLabelColor = Accent
                                    )
                                )
                            }
                        }
                    }
                    if (showingEpisodes) {
                        TextButton(onClick = { showingEpisodes = false }) { Text("← Back to series") }
                    }
                    val displayList = if (showingEpisodes) episodes else filtered
                    if (displayList.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (tab == 3) "No saved titles yet" else "Nothing found", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (tab == 3) "Tap the heart on a title to save it here." else "Try another search or category.",
                                    color = TextSecondary, fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(displayList, key = { it.url + it.name + it.streamId }) { item ->
                                val key = favoriteKey(item)
                                ContentCard(
                                    item = item,
                                    favorite = favorites.contains(key),
                                    onFavorite = {
                                        favorites = (if (favorites.contains(key)) favorites - key else favorites + key)
                                        preferences.edit().putStringSet("favorites", favorites).apply()
                                    },
                                    onOpen = {
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
                                    }
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrandHeader()
                    Spacer(Modifier.height(28.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(SurfaceOne).border(1.dp, Stroke, RoundedCornerShape(24.dp))
                            .padding(22.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("WELCOME TO NEXORA", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Your entertainment.\nYour way.", color = TextPrimary, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Connect a source you are authorized to use and bring your live channels, films and series together.",
                                color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FeatureTag("LIVE TV")
                                FeatureTag("MOVIES")
                                FeatureTag("SERIES")
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Column(
                        Modifier.fillMaxWidth().widthIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("CONNECT YOUR SOURCE", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(SurfaceOne).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SourceMode("Xtream Codes", mode == 1, Modifier.weight(1f)) { mode = 1; message = null }
                            SourceMode("M3U / M3U8", mode == 0, Modifier.weight(1f)) { mode = 0; message = null }
                        }
                        if (mode == 1) {
                            NexoraField(
                                value = server, onValueChange = { server = it },
                                label = "Server address", placeholder = "https://provider.example:8080",
                                keyboardType = KeyboardType.Uri
                            )
                            NexoraField(
                                value = username, onValueChange = { username = it },
                                label = "Username", placeholder = "Enter your provider username"
                            )
                            NexoraField(
                                value = password, onValueChange = { password = it },
                                label = "Password", placeholder = "Enter your provider password",
                                secret = true
                            )
                        } else {
                            NexoraField(
                                value = sourceUrl, onValueChange = { sourceUrl = it },
                                label = "Playlist URL", placeholder = "https://example.com/playlist.m3u",
                                keyboardType = KeyboardType.Uri
                            )
                        }
                        message?.let {
                            Text(it, color = Danger, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Button(
                            onClick = {
                                busy = true
                                message = null
                                showingEpisodes = false
                                val done: (IptvCatalog?, String?) -> Unit = { result, error ->
                                    if (result != null) {
                                        catalog = result
                                        tab = when {
                                            result.live.isNotEmpty() -> 0
                                            result.movies.isNotEmpty() -> 1
                                            result.series.isNotEmpty() -> 2
                                            else -> 0
                                        }
                                        category = "All"
                                        query = ""
                                        connected = true
                                    } else {
                                        message = error ?: "Could not connect to this source."
                                    }
                                    busy = false
                                }
                                if (mode == 0) onLoadM3u(sourceUrl.trim(), done)
                                else onXtream(server.trim(), username.trim(), password, done)
                            },
                            enabled = !busy && if (mode == 0) sourceUrl.isNotBlank()
                                else server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Canvas, strokeWidth = 2.dp)
                            else Text("CONNECT & LOAD LIBRARY", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                        Text(
                            "NEXORA is a media player only. No channels or subscriptions are included.",
                            color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
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
                        Text(program.title, color = Accent, fontWeight = FontWeight.Bold)
                        Text(listOf(program.start, program.end).filter { it.isNotBlank() }.joinToString(" – "), color = TextSecondary, fontSize = 11.sp)
                        if (program.description.isNotBlank()) Text(program.description, fontSize = 12.sp)
                        HorizontalDivider(Modifier.padding(vertical = 7.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEpg = false }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun BrandHeader(trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(AccentDark),
            contentAlignment = Alignment.Center
        ) {
            Text("N", color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("NEXORA", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.8.sp)
            Text("ENTERTAINMENT PLAYER", color = Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        trailing?.invoke()
    }
}

@Composable
private fun FeatureTag(label: String) {
    Surface(color = AccentDark, shape = RoundedCornerShape(20.dp)) {
        Text(label, color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SourceMode(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentDark else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Accent else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NexoraField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.65f), fontSize = 12.sp) },
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Stroke,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent
            )
        )
    }
}

@Composable
private fun StatPill(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(13.dp)).background(SurfaceOne).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
        Column {
            Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 8.sp, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun LibraryTab(label: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentDark else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected) Accent else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = if (selected) Accent.copy(alpha = 0.8f) else TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun ContentCard(
    item: Channel,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(SurfaceOne).border(1.dp, Stroke.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
            .clickable(onClick = onOpen).focusable()
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceTwo),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when (item.type) {
                    MediaType.LIVE -> Icons.Default.LiveTv
                    MediaType.MOVIE -> Icons.Default.Movie
                    MediaType.SERIES -> Icons.Default.Tv
                },
                contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                item.group.ifBlank { when (item.type) { MediaType.LIVE -> "Live TV"; MediaType.MOVIE -> "Movies"; MediaType.SERIES -> "Series" } },
                color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (favorite) "Remove from saved" else "Save title",
                tint = if (favorite) Color(0xFFFF7896) else TextSecondary
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(AccentDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Accent)
        }
    }
}
