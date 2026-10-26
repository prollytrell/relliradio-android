package com.relli.radio.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.relli.radio.RelliApp
import com.relli.radio.playback.toSong
import com.relli.radio.ui.player.ArtTone
import com.relli.radio.ui.player.MiniPlayer
import com.relli.radio.ui.player.NowPlaying
import com.relli.radio.ui.screens.AlbumScreen
import com.relli.radio.ui.screens.ArtistScreen
import com.relli.radio.ui.screens.GenreScreen
import com.relli.radio.ui.screens.HomeScreen
import com.relli.radio.ui.screens.LibraryScreen
import com.relli.radio.ui.screens.LoginScreen
import com.relli.radio.ui.screens.PlaylistScreen
import com.relli.radio.ui.screens.SearchScreen
import com.relli.radio.ui.screens.SettingsScreen
import com.relli.radio.ui.theme.Relli
import com.relli.radio.ui.theme.RelliTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) Nav.playerOpen.value = true
        // Phones stay upright; TVs and tablets can use landscape.
        val isTv = (getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        if (!isTv && resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            RelliTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.relli.radio.ui.components.LocalIsTv provides isTv) {
                    com.relli.radio.ui.components.FocusRings { Root() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) Nav.playerOpen.value = true
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "openPlayer"
    }
}

@Composable
private fun Root() {
    val prefs = RelliApp.prefs
    var signedIn by remember { mutableStateOf(prefs.credentials != null && prefs.password.value.isNotEmpty()) }
    // Surface sets cream as the default text color for everything inside it.
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Relli.bg, contentColor = Relli.cream) {
        if (!signedIn) LoginScreen { signedIn = true }
        else AppShell(onSignOut = { signedIn = false })
    }
}

private data class Tab(val route: String, val icon: ImageVector, val label: String)
private val TABS = listOf(
    Tab("home", Icons.Rounded.Home, "Home"),
    Tab("library/albums", Icons.Rounded.LibraryMusic, "Library"),
    Tab("search", Icons.Rounded.Search, "Search"),
    Tab("settings", Icons.Rounded.Settings, "Settings"),
)

@Composable
private fun AppShell(onSignOut: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { Playback.connect(context.applicationContext) }

    // Android 13+: the media notification needs permission to show.
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS) }

    // Album-art colors follow the current song.
    val current by Playback.current.collectAsState()
    LaunchedEffect(current?.mediaId) { runCatching { ArtTone.update(current?.toSong()?.let { it.coverArt ?: it.albumId }) } }

    val nav = rememberNavController()
    Nav.controller = nav
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val playerOpen by Nav.playerOpen.collectAsState()
    val density = LocalDensity.current
    var bottomHeight by remember { mutableStateOf(140.dp) }

    val isTv = remember { isTelevision(context) }
    val navigate: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(Modifier.fillMaxSize()) {
        // TV: a rail on the left, always one press of the remote's Left button away,
        // with Now Playing at the top.
        if (isTv) TvRail(route, navigate)

        Box(Modifier.weight(1f).fillMaxHeight()) {
            NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                composable("home") { HomeScreen(bottomHeight) }
                composable(
                    "library/{tab}?sort={sort}",
                    arguments = listOf(navArgument("tab") { type = NavType.StringType }, navArgument("sort") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { e -> LibraryScreen(e.arguments?.getString("tab") ?: "albums", e.arguments?.getString("sort"), bottomHeight) }
                composable("search") { SearchScreen(bottomHeight) }
                composable("settings") { SettingsScreen(bottomHeight, onSignOut) }
                composable("album/{id}") { e -> AlbumScreen(e.arguments?.getString("id") ?: "", bottomHeight) }
                composable("artist/{id}") { e -> ArtistScreen(e.arguments?.getString("id") ?: "", bottomHeight) }
                composable("playlist/{id}") { e -> PlaylistScreen(e.arguments?.getString("id") ?: "", bottomHeight) }
                composable("genre/{name}") { e -> GenreScreen(e.arguments?.getString("name") ?: "", bottomHeight) }
            }

            // Phone: mini player + bottom navigation (icons only, the active one in a gold pill).
            // TV: just the handoff bar here; navigation lives in the rail.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Relli.bg.copy(alpha = 0f), Relli.bg, Relli.bg), startY = 0f, endY = 60f))
                    .navigationBarsPadding()
                    .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } },
            ) {
                HandoffBanner()
                if (!isTv) {
                    MiniPlayer(Modifier.padding(top = 10.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TABS.forEach { tab ->
                            val active = route.substringBefore("/") == tab.route.substringBefore("/")
                            Box(
                                Modifier.width(64.dp).height(38.dp).clip(CircleShape)
                                    .then(if (active) Modifier.background(Relli.goldMetal) else Modifier)
                                    .clickable { navigate(tab.route) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(tab.icon, tab.label, tint = if (active) Relli.onGold else Relli.faint) }
                        }
                    }
                }
            }
        }
    }

    if (isTv) {
        // TV: Now Playing opens in its own window, so the remote's arrows stay inside it.
        if (playerOpen && current != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { Nav.playerOpen.value = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(shown, enter = slideInVertically(tween(280)) { it }, exit = slideOutVertically(tween(240)) { it }) {
                    NowPlaying()
                }
            }
        }
    } else {
        // Phones and tablets: Now Playing slides up over the app. (A separate window doesn't get
        // the phone's screen-edge sizes, which pushed the bottom toolbar under the navigation bar.)
        AnimatedVisibility(
            visible = playerOpen && current != null,
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(240)) { it },
        ) { NowPlaying() }
    }
}

fun isTelevision(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun TvRail(route: String, navigate: (String) -> Unit) {
    val current by Playback.current.collectAsState()
    val playing by Playback.playing.collectAsState()
    Column(
        Modifier.width(96.dp).fillMaxHeight().background(Relli.bgDeep).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.relli.radio.R.drawable.rr_logo), "RelliRadio", Modifier.size(52.dp))
        current?.let { item ->
            val song = remember(item) { item.toSong() }
            Column(
                Modifier.width(76.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { Nav.playerOpen.value = true }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    com.relli.radio.ui.components.Cover(song.coverArt ?: song.albumId, Modifier.size(60.dp), 160, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    if (playing) Icon(Icons.Rounded.GraphicEq, null, tint = Relli.cream,
                        modifier = Modifier.size(26.dp).background(Relli.bgDeep.copy(alpha = 0.6f), CircleShape).padding(3.dp))
                }
                androidx.compose.material3.Text("Now playing", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Relli.cream,
                    modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            }
        }
        TABS.forEach { tab ->
            val active = route.substringBefore("/") == tab.route.substringBefore("/")
            Column(
                Modifier.width(76.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .then(if (active) Modifier.background(Relli.goldMetal) else Modifier)
                    .clickable { navigate(tab.route) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, tab.label, tint = if (active) Relli.onGold else Relli.muted)
                androidx.compose.material3.Text(tab.label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = if (active) Relli.onGold else Relli.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * "Pick up where you left off": while the app is open it asks Navidrome every 10 seconds which device
 * played last. If another device (TV, desktop, another phone) is newer, offer to continue from there.
 * The other device pauses on its own once this one takes over (see Handoff).
 */
@Composable
private fun HandoffBanner() {
    val offer by com.relli.radio.playback.Handoff.offer.collectAsState()
    val playing by Playback.playing.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            // Back in the app after a while: show any music added since.
            Library.refreshIfStale()
            while (true) {
                // While playing here, the playback service does this check itself.
                if (!Playback.playing.value) com.relli.radio.playback.Handoff.check()
                kotlinx.coroutines.delay(3_000)
            }
        }
    }
    if (playing && offer != null && !com.relli.radio.playback.Handoff.passive) return
    val q = offer ?: return
    val song = q.songs.firstOrNull { it.id == q.currentId } ?: q.songs.first()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(Relli.raise).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text("Continue from ${q.changedBy?.removePrefix("RelliRadio")?.trim()?.trim('(', ')')?.ifBlank { null } ?: "your other device"}?",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = Relli.gold)
            androidx.compose.material3.Text("${song.title} · ${com.relli.radio.ui.components.fmtTime(q.livePositionMs() / 1000)}",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        com.relli.radio.ui.components.PillButton("Play here", null, primary = true) {
            scope.launch {
                try { com.relli.radio.playback.PlayerHub.resumeFromServer() }
                catch (e: Exception) { com.relli.radio.ui.components.toast(e.message ?: "Couldn't resume") }
            }
        }
        com.relli.radio.ui.components.IconButton(onClick = { com.relli.radio.playback.Handoff.dismiss() }) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Not now", tint = Relli.muted)
        }
    }
}
