package com.relli.reels.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.reels.ReelsApp
import com.relli.reels.ui.components.Chip
import com.relli.reels.ui.components.PillButton
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.theme.Relli

private val QUALITY = listOf(0 to "Original", 20 to "20 Mbps", 10 to "10 Mbps", 6 to "6 Mbps", 3 to "3 Mbps", 1 to "1 Mbps")

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) = Column {
    Text(title, color = Relli.gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp, top = 26.dp, bottom = 10.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Relli.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
private fun Label(text: String, hint: String? = null) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
    }
}

@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (v, label) -> Chip(label, v == selected) { onPick(v) } }
    }
}

@Composable
private fun Toggle(text: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!on) }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { Label(text, hint) }
        Switch(on, null, colors = SwitchDefaults.colors(checkedThumbColor = Relli.cream, checkedTrackColor = Relli.gold.copy(alpha = 0.7f), uncheckedTrackColor = Relli.raise))
    }
}

@Composable
fun SettingsScreen(bottom: Dp, onSignOut: () -> Unit) {
    val p = ReelsApp.prefs
    val wifi by p.wifiQuality.flow.collectAsState()
    val mobile by p.mobileQuality.flow.collectAsState()
    val subs by p.subtitles.flow.collectAsState()
    val subSize by p.subtitleSize.flow.collectAsState()
    val autoNext by p.autoPlayNext.flow.collectAsState()
    val skip by p.skipIntro.flow.collectAsState()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = gutter(), end = gutter(), bottom = bottom + 30.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp)) }
        item {
            Column(Modifier.widthIn(max = 760.dp)) {
                Group("Streaming quality") {
                    Label("At home (Wi-Fi / Ethernet)", "Original plays the file as-is, the best picture. Lower settings make the server shrink the video.")
                    Choices(QUALITY, wifi) { p.wifiQuality.value = it }
                    Label("On mobile data", "Away from home over WireGuard, a lower setting starts faster and saves data.")
                    Choices(QUALITY, mobile) { p.mobileQuality.value = it }
                }
                Group("Watching") {
                    Toggle("Play the next episode automatically", "Starts the next one after a 15-second countdown when the credits roll.", autoNext) { p.autoPlayNext.value = it }
                    Label("Skip intro", "Needs intro markers on your server (the Intro Skipper plugin for Jellyfin).")
                    Choices(listOf("button" to "Show a button", "auto" to "Skip automatically", "off" to "Off"), skip) { p.skipIntro.value = it }
                }
                Group("Subtitles") {
                    Choices(listOf("auto" to "Only when needed", "on" to "Always on", "off" to "Off"), subs) { p.subtitles.value = it }
                    Label("\"Only when needed\" shows forced subtitles (for foreign-language scenes) and follows your Jellyfin subtitle settings.")
                    Label("Size")
                    Choices(listOf("s" to "Small", "m" to "Medium", "l" to "Large"), subSize) { p.subtitleSize.value = it }
                }
                Group("Account") {
                    Label("Signed in as ${p.userName.value}", "${p.serverName.value.ifBlank { "Jellyfin" }} at ${p.server.value}")
                    PillButton("Sign out", null) { onSignOut() }
                }
                Text("RelliReels · talks only to your own Jellyfin server.", color = Relli.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 24.dp, start = 6.dp))
            }
        }
    }
}
