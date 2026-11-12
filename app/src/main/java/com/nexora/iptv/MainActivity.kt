package com.nexora.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nexora.iptv.data.Channel
import com.nexora.iptv.data.IptvRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository = IptvRepository()
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF55D6BE), secondary = Color(0xFF8AA8FF),
                background = Color(0xFF080D18), surface = Color(0xFF111A2A)
            )) {
                NexoraApp(
                    onLoadM3u = { url, done -> lifecycleScope.launch {
                        runCatching { repository.loadM3u(url) }.onSuccess { done(it, null) }
                            .onFailure { done(emptyList(), it.message ?: "Unable to load playlist") }
                    } },
                    onXtream = { server, user, pass, done -> lifecycleScope.launch {
                        runCatching { repository.loginXtream(server, user, pass) }.onSuccess { done(it, null) }
                            .onFailure { done(emptyList(), it.message ?: "Login failed") }
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
                        }
                    }, modifier = Modifier.fillMaxWidth().height(220.dp)) }
                )
            }
        }
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}

@Composable
private fun NexoraApp(
    onLoadM3u: (String, (List<Channel>, String?) -> Unit) -> Unit,
    onXtream: (String, String, String, (List<Channel>, String?) -> Unit) -> Unit,
    onPlay: (Channel) -> Unit,
    playerView: @Composable () -> Unit
) {
    var mode by remember { mutableStateOf(0) }
    var playlist by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFF080D18)).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEXORA", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("IPTV PLAYER", color = Color(0xFF55D6BE), fontSize = 11.sp, letterSpacing = 4.sp)
            }
            Surface(color = Color(0xFF142C35), shape = RoundedCornerShape(50)) {
                Text("PLAYER", color = Color(0xFF55D6BE), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 11.sp)
            }
        }
        if (selected != null) {
            Text(selected!!.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            playerView()
            TextButton(onClick = { selected = null }) { Text("Back to channels") }
        } else {
            Text("Your entertainment, your sources.", color = Color(0xFFB6C2D5), fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("M3U playlist") })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Xtream login") })
            }
            if (mode == 0) {
                OutlinedTextField(playlist, { playlist = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTPS playlist URL") }, singleLine = true)
            } else {
                OutlinedTextField(server, { server = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTPS server URL") }, singleLine = true)
                OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") }, singleLine = true)
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                busy = true
                message = null
                val callback: (List<Channel>, String?) -> Unit = { list, error ->
                    channels = list
                    message = error
                    busy = false
                }
                if (mode == 0) onLoadM3u(playlist.trim(), callback)
                else onXtream(server.trim(), username.trim(), password, callback)
            }, enabled = !busy && if (mode == 0) playlist.isNotBlank()
                else server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("CONNECT SOURCE")
            }
            message?.let { Text(it, color = Color(0xFFFF8C8C), modifier = Modifier.padding(top = 8.dp)) }
            if (channels.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search channels") }, singleLine = true)
                Text("${channels.size} channels", color = Color(0xFF55D6BE),
                    modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(channels.filter { it.name.contains(query, true) || it.group.contains(query, true) }) { channel ->
                        Surface(color = Color(0xFF111A2A), shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = channel
                                onPlay(channel)
                            }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF55D6BE))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(channel.group, color = Color(0xFF9AA9BF), fontSize = 12.sp)
                                }
                                Text("PLAY", color = Color(0xFF55D6BE), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
