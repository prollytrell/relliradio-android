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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
        setContent { RelliTheme { Root() } }
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
    Box(Modifier.fillMaxSize().background(Relli.bg)) {
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

    Box(Modifier.fillMaxSize()) {
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

        // Mini player + bottom navigation (icons only, the active one in a gold pill)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Relli.bg.copy(alpha = 0f), Relli.bg, Relli.bg), startY = 0f, endY = 60f))
                .navigationBarsPadding()
                .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } },
        ) {
            MiniPlayer(Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TABS.forEach { tab ->
                    val active = route.substringBefore("/") == tab.route.substringBefore("/")
                    Box(
                        Modifier.width(64.dp).height(38.dp).clip(CircleShape)
                            .then(if (active) Modifier.background(Relli.goldMetal) else Modifier)
                            .clickable {
                                nav.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(tab.icon, tab.label, tint = if (active) Relli.onGold else Relli.faint) }
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen && current != null,
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(240)) { it },
        ) { NowPlaying() }
    }
}
