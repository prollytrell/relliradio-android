package com.relli.reels.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.relli.reels.R
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.components.FocusRings
import com.relli.reels.ui.components.IconButton
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.PillButton
import com.relli.reels.ui.components.Refresh
import com.relli.reels.ui.components.timeLeft
import com.relli.reels.ui.screens.BrowseScreen
import com.relli.reels.ui.screens.DetailScreen
import com.relli.reels.ui.screens.HomeScreen
import com.relli.reels.ui.screens.LiveScreen
import com.relli.reels.ui.screens.LoginScreen
import com.relli.reels.ui.screens.MyStuffScreen
import com.relli.reels.ui.screens.SearchScreen
import com.relli.reels.ui.screens.SettingsScreen
import com.relli.reels.ui.theme.Relli
import com.relli.reels.ui.theme.RelliTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val tv = isTelevision(this)
        // Phones browse upright (the player turns sideways on its own); TVs and tablets use landscape.
        if (!tv && resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            RelliTheme {
                CompositionLocalProvider(LocalIsTv provides tv) { FocusRings { Root() } }
            }
        }
    }
}

fun isTelevision(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun Root() {
    var signedIn by remember { mutableStateOf(ReelsApp.prefs.signedIn) }
    Surface(Modifier.fillMaxSize(), color = Relli.bg, contentColor = Relli.cream) {
        if (!signedIn) LoginScreen { signedIn = true }
        else AppShell(onSignOut = { ReelsApp.prefs.signOut(); signedIn = false })
    }
}

private data class Tab(val route: String, val icon: ImageVector, val label: String)

private val TABS = listOf(
    Tab("home", Icons.Rounded.Home, "Home"),
    Tab("browse", Icons.Rounded.Movie, "Browse"),
    Tab("live", Icons.Rounded.LiveTv, "Live TV"),
    Tab("search", Icons.Rounded.Search, "Search"),
    Tab("mystuff", Icons.Rounded.Bookmarks, "My Stuff"),
)
private val SETTINGS = Tab("settings", Icons.Rounded.Settings, "Settings")

@Composable
private fun AppShell(onSignOut: () -> Unit) {
    val tv = LocalIsTv.current
    val nav = rememberNavController()
    Nav.controller = nav
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val density = LocalDensity.current
    var bottom by remember { mutableStateOf(90.dp) }
    val pad = if (tv) 30.dp else bottom

    val navigate: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(Modifier.fillMaxSize()) {
        if (tv) TvRail(route, navigate)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                composable("home") { HomeScreen(pad) }
                composable(
                    "browse?view={view}",
                    arguments = listOf(navArgument("view") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { e -> BrowseScreen(e.arguments?.getString("view"), pad) }
                composable("live") { LiveScreen(pad) }
                composable("search") { SearchScreen(pad) }
                composable("mystuff") { MyStuffScreen(pad) }
                composable("settings") { SettingsScreen(pad, onSignOut) }
                composable(
                    "item/{id}?season={season}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("season") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { e -> DetailScreen(e.arguments?.getString("id") ?: "", e.arguments?.getString("season"), pad) }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .then(if (!tv) Modifier.background(Brush.verticalGradient(listOf(Relli.bg.copy(alpha = 0f), Relli.bg, Relli.bg), startY = 0f, endY = 50f)) else Modifier)
                    .navigationBarsPadding()
                    .onSizeChanged { bottom = with(density) { it.height.toDp() } },
            ) {
                ContinueBanner()
                if (!tv) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TABS.forEach { tab ->
                            val active = route.substringBefore("?").substringBefore("/") == tab.route
                            Column(
                                Modifier.width(66.dp).clip(RoundedCornerShape(16.dp)).clickable { navigate(tab.route) }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.width(56.dp).height(30.dp).clip(CircleShape).then(if (active) Modifier.background(Relli.goldMetal) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(tab.icon, tab.label, tint = if (active) Relli.onGold else Relli.faint, modifier = Modifier.size(22.dp)) }
                                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (active) Relli.cream else Relli.faint, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvRail(route: String, navigate: (String) -> Unit) {
    Column(
        Modifier.width(104.dp).fillMaxHeight().background(Relli.bgDeep).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(painterResource(R.drawable.rr_logo), "RelliReels", Modifier.size(54.dp))
        Text("Reels", style = MaterialTheme.typography.labelMedium, color = Relli.gold, modifier = Modifier.padding(bottom = 8.dp))
        (TABS + SETTINGS).forEach { tab ->
            val active = route.substringBefore("?").substringBefore("/") == tab.route
            Column(
                Modifier.width(84.dp).clip(RoundedCornerShape(16.dp))
                    .then(if (active) Modifier.background(Relli.goldMetal) else Modifier)
                    .clickable { navigate(tab.route) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, tab.label, tint = if (active) Relli.onGold else Relli.muted)
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (active) Relli.onGold else Relli.muted, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
            }
        }
    }
}

/**
 * "Pick up where you left off": if you were watching something on another device in the last
 * 30 minutes (TV, phone, computer), offer to continue it here. Jellyfin keeps the spot for every device.
 */
@Composable
private fun ContinueBanner() {
    var offer by remember { mutableStateOf<Item?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                offer = runCatching {
                    ReelsApp.api.resume(3).firstOrNull { i ->
                        val at = i.userData?.lastPlayedDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        at != null && Duration.between(at, Instant.now()).toMinutes() < 30 && i.id !in Continue.dismissed
                    }
                }.getOrNull()
                delay(20_000)
            }
        }
    }
    val i = offer ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp))
            .background(Relli.raise).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Pick up where you left off", style = MaterialTheme.typography.labelMedium, color = Relli.gold)
            Text(listOfNotNull(i.seriesName, i.episodeCode, i.name.takeIf { i.seriesName == null }, timeLeft(i)).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PillButton("Play", null, primary = true) { Continue.dismissed += i.id; offer = null; Nav.play(i) }
        IconButton(onClick = { Continue.dismissed += i.id; offer = null; Refresh.bump() }) { Icon(Icons.Rounded.Close, "Not now", tint = Relli.muted) }
    }
}

object Continue {
    /** Items you dismissed (or started) here, so the banner doesn't keep offering them this session. */
    val dismissed = mutableSetOf<String>()
}
