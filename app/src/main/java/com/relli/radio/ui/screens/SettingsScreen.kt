package com.relli.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.radio.RelliApp
import com.relli.radio.playback.PlayerHub
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Gutter
import com.relli.radio.ui.components.toast
import com.relli.radio.ui.player.ActionRow
import com.relli.radio.ui.player.ChipRow
import com.relli.radio.ui.player.EqualizerSheet
import com.relli.radio.ui.player.SleepSheet
import com.relli.radio.ui.player.SmartSheet
import com.relli.radio.ui.player.SpeedSheet
import com.relli.radio.ui.player.ToggleRow
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.launch

private val QUALITIES = listOf("0" to "Original", "320" to "320 kbps", "192" to "192 kbps", "128" to "128 kbps")

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) = Column {
    Text(title, color = Relli.gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp, top = 26.dp, bottom = 10.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Relli.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
private fun Label(text: String, hint: String? = null) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
    }
}

@Composable
fun SettingsScreen(bottom: Dp, onSignOut: () -> Unit) {
    val prefs = RelliApp.prefs
    val wifi by prefs.wifiQuality.flow.collectAsState()
    val mobile by prefs.mobileQuality.flow.collectAsState()
    val visual by prefs.visual.flow.collectAsState()
    val lyricsSize by prefs.lyricsSize.flow.collectAsState()
    val openOnSelect by prefs.openPlayerOnSelect.flow.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    var editHome by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom + 30.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp)) }
        item {
            Group("Streaming") {
                Label("On Wi-Fi", "Original files sound best and use the most data.")
                Column(Modifier.padding(horizontal = 6.dp)) { ChipRow(QUALITIES, wifi.toString()) { prefs.wifiQuality.value = it.toInt() } }
                Label("On mobile data", "Lower settings save data and play better on weak signal.")
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { ChipRow(QUALITIES, mobile.toString()) { prefs.mobileQuality.value = it.toInt() } }
            }
        }
        item {
            Group("Playback") {
                ActionRow(Icons.Rounded.Equalizer, "Equalizer, DSP and ReplayGain") { sheet = "eq" }
                ActionRow(Icons.Rounded.Speed, "Speed and pitch") { sheet = "speed" }
                ActionRow(Icons.Rounded.Bedtime, "Sleep timer") { sheet = "sleep" }
                ActionRow(Icons.Rounded.AutoAwesome, "Smart queue and Smart flow") { sheet = "smart" }
                ActionRow(Icons.Rounded.CloudDownload, "Resume from another device") {
                    scope.launch {
                        try { if (PlayerHub.resumeFromServer()) toast("Picked up where you left off") else toast("No saved queue on the server yet.") }
                        catch (e: Exception) { toast(e.message ?: "Couldn't reach the server") }
                    }
                }
            }
        }
        item {
            Group("Interface") {
                ActionRow(Icons.Rounded.Edit, "Edit home widgets") { editHome = true }
                ToggleRow("Open Now Playing when you pick a song", openOnSelect, "Tapping a song in a list jumps straight to the player.") {
                    prefs.openPlayerOnSelect.value = it
                }
                Label("Now Playing shows")
                Column(Modifier.padding(horizontal = 6.dp)) { ChipRow(listOf("cover" to "Cover", "lyrics" to "Lyrics", "visualizer" to "Visualizer"), visual) { prefs.visual.value = it } }
                Label("Lyrics size")
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { ChipRow(listOf("s" to "Small", "m" to "Medium", "l" to "Large"), lyricsSize) { prefs.lyricsSize.value = it } }
            }
        }
        item {
            Group("Account") {
                Label("Signed in as ${prefs.user.value}", "${prefs.serverName.value} at ${prefs.server.value}")
                ActionRow(Icons.AutoMirrored.Rounded.Logout, "Sign out") {
                    Playback.clearUpcoming()
                    prefs.signOut()
                    onSignOut()
                }
            }
        }
        item {
            Text("RelliRadio for Android. Talks only to your own server.", color = Relli.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 24.dp, start = 6.dp))
        }
    }
    when (sheet) {
        "eq" -> EqualizerSheet { sheet = null }
        "speed" -> SpeedSheet { sheet = null }
        "sleep" -> SleepSheet { sheet = null }
        "smart" -> SmartSheet { sheet = null }
    }
    if (editHome) EditHomeSheet { editHome = false }
}
