package com.relli.radio.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.relli.radio.R
import com.relli.radio.RelliApp
import com.relli.radio.data.Credentials
import com.relli.radio.ui.components.PillButton
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.launch

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Relli.gold, unfocusedBorderColor = Relli.line, cursorColor = Relli.gold,
    focusedLabelColor = Relli.gold, unfocusedLabelColor = Relli.muted,
    focusedContainerColor = Relli.surface, unfocusedContainerColor = Relli.surface,
)

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val prefs = RelliApp.prefs
    var server by remember { mutableStateOf(prefs.server.value.ifEmpty { "http://192.168.1.10:4533" }) }
    var user by remember { mutableStateOf(prefs.user.value) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        busy = true; error = null
        var address = server.trim().trimEnd('/')
        // Local addresses (192.168.x.x, Navidrome's :4533 port) use plain http; names use https.
        if (!address.startsWith("http")) {
            val local = Regex("""^(\d{1,3}\.){3}\d{1,3}(:\d+)?(/.*)?$""").matches(address) || address.contains(":4533")
            address = (if (local) "http://" else "https://") + address
        }
        val creds = Credentials(address, user.trim(), password)
        scope.launch {
            try {
                val name = RelliApp.api.login(creds)
                prefs.server.value = address; prefs.user.value = creds.user; prefs.password.value = password; prefs.serverName.value = name
                onDone()
            } catch (e: Exception) {
                error = e.message
            } finally { busy = false }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Relli.gold.copy(alpha = 0.12f), Relli.bg), radius = 1400f))
            .verticalScroll(rememberScrollState()).imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(24.dp).padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(painterResource(R.drawable.rr_logo), "RelliRadio", Modifier.size(112.dp))
            Text("RelliRadio", style = MaterialTheme.typography.displaySmall, color = Relli.gold)
            Text("Sign in with your Navidrome account.", color = Relli.muted)
            val shape = RoundedCornerShape(14.dp)
            OutlinedTextField(server, { server = it }, label = { Text("Server address") }, singleLine = true, shape = shape, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, shape = shape, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, shape = shape, colors = fieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFF0A58F), style = MaterialTheme.typography.titleSmall) }
            PillButton(if (busy) "Signing in…" else "Sign in", null, primary = true, modifier = Modifier.fillMaxWidth()) { submit() }
            Text("Away from home? Turn on WireGuard first; the same address works through the VPN.", color = Relli.faint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
