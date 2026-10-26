package com.relli.home.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import com.relli.home.HomeApp
import com.relli.home.data.App
import com.relli.home.data.Weather
import com.relli.home.media.NowPlaying
import com.relli.home.ui.components.Chip
import com.relli.home.ui.components.PillButton
import com.relli.home.ui.components.toast
import com.relli.home.ui.theme.Relli
import kotlinx.coroutines.launch

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) = Column {
    Text(title, color = Relli.gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp, top = 26.dp, bottom = 10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Relli.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun Label(text: String, hint: String? = null) = Column(Modifier.padding(horizontal = 4.dp)) {
    Text(text, style = MaterialTheme.typography.titleSmall)
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
}

@Composable
private fun Toggle(text: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!on) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Label(text, hint) }
        Switch(on, null, colors = SwitchDefaults.colors(checkedThumbColor = Relli.cream, checkedTrackColor = Relli.gold.copy(alpha = 0.7f), uncheckedTrackColor = Relli.raise))
    }
}

@Composable
private fun Command(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Relli.cream,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Relli.bgDeep).padding(10.dp))
}

private fun overrideOn(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains("${context.packageName}/") == true

@Composable
fun SettingsScreen(allApps: List<App>, onClose: () -> Unit) {
    val context = LocalContext.current
    val p = HomeApp.prefs
    val scope = rememberCoroutineScope()
    val city by p.city.flow.collectAsState()
    val fahrenheit by p.fahrenheit.flow.collectAsState()
    val clock24 by p.clock24.flow.collectAsState()
    val hidden by p.hidden.flow.collectAsState()
    val takeOver by p.takeOverHome.flow.collectAsState()
    val nowPlayingAllowed by NowPlaying.allowed.collectAsState()
    var cityInput by remember { mutableStateOf(city) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onClose)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 64.dp, vertical = 30.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RelliHome settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                PillButton("Done", null, primary = true, modifier = Modifier.focusRequester(first), onClick = onClose)
            }
        }
        item {
            Column(Modifier.widthIn(max = 820.dp)) {
                Group("Weather and clock") {
                    Label("Your city", if (city.isBlank()) "Type a city, then Save. Weather comes from Open-Meteo, which only receives the city's rough location." else "Showing weather for $city")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(cityInput, { cityInput = it }, singleLine = true, placeholder = { Text("e.g. Houston") }, shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Relli.gold, unfocusedBorderColor = Relli.line, cursorColor = Relli.gold,
                                focusedTextColor = Relli.cream, unfocusedTextColor = Relli.cream, focusedContainerColor = Relli.bgDeep, unfocusedContainerColor = Relli.bgDeep),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { }),
                            modifier = Modifier.width(360.dp))
                        PillButton("Save", null, primary = true, modifier = Modifier.padding(start = 12.dp)) {
                            scope.launch {
                                val place = runCatching { Weather.find(cityInput.trim()) }.getOrNull()
                                if (place == null) toast("Couldn't find that city.")
                                else {
                                    p.lat.value = place.latitude.toFloat(); p.lon.value = place.longitude.toFloat()
                                    p.city.value = listOfNotNull(place.name, place.admin1).joinToString(", ")
                                    toast("Weather set to ${p.city.value}")
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("°F", fahrenheit) { p.fahrenheit.value = true }
                        Chip("°C", !fahrenheit) { p.fahrenheit.value = false }
                        Chip("12-hour clock", !clock24) { p.clock24.value = false }
                        Chip("24-hour clock", clock24) { p.clock24.value = true }
                    }
                }

                Group("Rows from RelliReels") {
                    val c by p.showContinue.flow.collectAsState()
                    val l by p.showLive.flow.collectAsState()
                    val n by p.showNew.flow.collectAsState()
                    Toggle("Continue watching", "What you're in the middle of, and the next episodes.", c) { p.showContinue.value = it }
                    Toggle("On now", "Live TV channels and what's airing.", l) { p.showLive.value = it }
                    Toggle("Recently added", "The newest movies and shows on your server.", n) { p.showNew.value = it }
                }

                Group("Apps") {
                    Label("Rearrange or hide apps", "On the home screen, hold OK on an app (or press the Menu button) to move it or hide it.")
                    if (hidden.isNotEmpty()) {
                        Label("Hidden apps")
                        hidden.forEach { pkg ->
                            val label = allApps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { p.hidden.value = hidden - pkg }.padding(8.dp)) {
                                Text(label, modifier = Modifier.weight(1f)); Text("Show again", color = Relli.gold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    PillButton("Reset app order", null) { p.appOrder.value = emptyList(); toast("App order reset") }
                }

                Group("Home button") {
                    val on = overrideOn(context)
                    Label(if (on) "RelliHome opens when you press Home." else "Not set up yet",
                        "Google TV doesn't let you choose a home screen in Settings, so RelliHome uses an accessibility helper that only watches for the stock home screen opening.")
                    Toggle("Take over the Home button", null, takeOver) { p.takeOverHome.value = it }
                    if (!on) {
                        Label("Turn it on from your computer (with the TV connected over ADB):")
                        Command("adb shell settings put secure enabled_accessibility_services com.relli.home/com.relli.home.HomeOverrideService")
                        Command("adb shell settings put secure accessibility_enabled 1")
                        PillButton("Or open Accessibility settings", null) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                }

                Group("Now playing") {
                    val allowed = nowPlayingAllowed || NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                    Label(if (allowed) "On: music and video from any app shows on the home screen." else "Not allowed yet",
                        "Android only shares what's playing with apps allowed to see notifications. RelliHome uses that to show the song or show, never to read your notifications.")
                    if (!allowed) Command("adb shell cmd notification allow_listener com.relli.home/com.relli.home.media.MediaListener")
                }

                Text("RelliHome · same look as RelliRadio and RelliReels.", color = Relli.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 24.dp, start = 6.dp))
            }
        }
    }
}

/** Hold OK on an app: move it, hide it, or open its info page. */
@Composable
fun AppMenu(context: Context, app: App, shown: List<App>, onClose: () -> Unit) {
    val p = HomeApp.prefs
    fun move(dir: Int) {
        val order = shown.map { it.pkg }.toMutableList()
        val i = order.indexOf(app.pkg)
        val j = (i + dir).coerceIn(0, order.lastIndex)
        if (i < 0 || i == j) return
        order.add(j, order.removeAt(i))
        p.appOrder.value = order + p.hidden.value
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(360.dp).clip(RoundedCornerShape(22.dp)).background(Relli.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
            PillButton("Move left", null, modifier = Modifier.fillMaxWidth().focusRequester(first)) { move(-1) }
            PillButton("Move right", null, modifier = Modifier.fillMaxWidth()) { move(1) }
            PillButton("Move to the front", null, modifier = Modifier.fillMaxWidth()) { move(-1000); onClose() }
            PillButton("Hide from the home screen", null, modifier = Modifier.fillMaxWidth()) { p.hidden.value = (p.hidden.value + app.pkg).distinct(); onClose() }
            PillButton("App info", null, modifier = Modifier.fillMaxWidth()) {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onClose()
            }
            PillButton("Done", null, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}
