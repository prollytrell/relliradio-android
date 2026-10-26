package com.relli.reels.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relli.reels.R
import com.relli.reels.ReelsApp
import com.relli.reels.data.AuthResult
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.PillButton
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Relli.gold, unfocusedBorderColor = Relli.line, cursorColor = Relli.gold,
    focusedLabelColor = Relli.gold, unfocusedLabelColor = Relli.muted,
    focusedContainerColor = Relli.surface, unfocusedContainerColor = Relli.surface,
    focusedTextColor = Relli.cream, unfocusedTextColor = Relli.cream,
    focusedPlaceholderColor = Relli.faint, unfocusedPlaceholderColor = Relli.faint,
)

/** Adds http:// to bare addresses like 192.168.1.10:8096 (and https:// to names). */
fun normalizeServer(input: String): String {
    var a = input.trim().trimEnd('/')
    if (!a.startsWith("http")) {
        val local = Regex("""^(\d{1,3}\.){3}\d{1,3}(:\d+)?(/.*)?$""").matches(a) || a.contains(":8096") || a.endsWith(".local") || a.contains(".local:")
        a = (if (local) "http://" else "https://") + a
    }
    return a
}

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val prefs = ReelsApp.prefs
    val api = ReelsApp.api
    val tv = LocalIsTv.current
    var server by remember { mutableStateOf(prefs.server.value.ifEmpty { "http://192.168.1.10:8096" }) }
    var user by remember { mutableStateOf(prefs.userName.value) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var qcSecret by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun finish(address: String, auth: AuthResult) {
        val name = runCatching { api.publicInfo(address).serverName }.getOrNull()
        prefs.server.value = address
        prefs.userId.value = auth.user.id
        prefs.userName.value = auth.user.name
        prefs.serverName.value = name ?: "Jellyfin"
        prefs.token.value = auth.token
        onDone()
    }

    fun submit() {
        if (busy) return
        busy = true; error = null
        val address = normalizeServer(server)
        scope.launch {
            try { finish(address, api.login(address, user.trim(), password)) }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }

    // Quick Connect: show a 6-digit code here; approve it in Jellyfin on your phone or computer
    // (Profile → Quick Connect). No typing a password with a TV remote.
    LaunchedEffect(qcSecret) {
        val secret = qcSecret ?: return@LaunchedEffect
        val address = normalizeServer(server)
        repeat(300) {
            delay(2000)
            val s = runCatching { api.quickConnectState(address, secret) }.getOrNull()
            if (s?.authenticated == true) {
                try { finish(address, api.quickConnectFinish(address, secret)) } catch (e: Exception) { error = e.message; code = null; qcSecret = null }
                return@LaunchedEffect
            }
        }
        code = null; qcSecret = null; error = "The code expired. Try again."
    }

    fun startQuickConnect() {
        error = null
        val address = normalizeServer(server)
        scope.launch {
            try { val qc = api.quickConnectStart(address); code = qc.code; qcSecret = qc.secret }
            catch (e: Exception) { error = e.message }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Relli.gold.copy(alpha = 0.12f), Relli.bg), radius = 1400f))
            .verticalScroll(rememberScrollState()).imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(24.dp).padding(top = if (tv) 10.dp else 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(painterResource(R.drawable.rr_logo), "RelliReels", Modifier.size(if (tv) 84.dp else 112.dp))
            Text("RelliReels", style = MaterialTheme.typography.displaySmall, color = Relli.gold)
            Text("Sign in with your Jellyfin account.", color = Relli.muted)
            val shape = RoundedCornerShape(14.dp)
            OutlinedTextField(server, { server = it }, label = { Text("Server address") }, singleLine = true, shape = shape, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            val c = code
            if (c != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Relli.surface).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Your code", color = Relli.muted, style = MaterialTheme.typography.labelLarge)
                    Text(c, color = Relli.gold, style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, letterSpacing = 6.sp))
                    Text("On a device that's already signed in to Jellyfin, open your profile → Quick Connect and enter this code.",
                        color = Relli.cream, style = MaterialTheme.typography.bodyMedium)
                    PillButton("Cancel", null) { code = null; qcSecret = null }
                }
            } else {
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, shape = shape, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, shape = shape, colors = fieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
                PillButton(if (busy) "Signing in…" else "Sign in", null, primary = true, modifier = Modifier.fillMaxWidth()) { submit() }
                PillButton("Sign in with a code (Quick Connect)", null, modifier = Modifier.fillMaxWidth()) { startQuickConnect() }
            }
            error?.let { Text(it, color = Color(0xFFF0A58F), style = MaterialTheme.typography.titleSmall) }
            Text("Jellyfin usually runs on port 8096. Away from home? Turn on WireGuard first; the same address works through the VPN.",
                color = Relli.faint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
