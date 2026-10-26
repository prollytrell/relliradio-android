package com.relli.home.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One app you can open from the home screen. */
data class App(
    val pkg: String,
    val label: String,
    /** The app's own 16:9 TV tile, if it has one. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

object Apps {
    /** Every app that can be opened from a home screen: TV apps first, phone-style apps too. */
    suspend fun load(context: Context): List<App> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        fun query(category: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        val found = LinkedHashMap<String, App>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in query(category)) {
                val ai = ri.activityInfo
                val pkg = ai.packageName
                if (pkg == context.packageName || pkg in found || pkg in NEVER_SHOW) continue
                val banner = runCatching { (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull()
                val icon = runCatching { ai.loadIcon(pm)?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
                found[pkg] = App(pkg, ai.loadLabel(pm).toString(), banner, icon)
            }
        }
        found.values.toList()
    }

    /** Puts apps in your saved order; new ones go at the end, A–Z. */
    fun arrange(apps: List<App>, order: List<String>, hidden: List<String>): List<App> {
        val byPkg = apps.associateBy { it.pkg }
        val known = order.mapNotNull { byPkg[it] }
        val rest = apps.filter { it.pkg !in order }.sortedWith(compareBy({ it.pkg !in PREFERRED }, { PREFERRED.indexOf(it.pkg) }, { it.label.lowercase() }))
        return (known + rest).filter { it.pkg !in hidden }
    }

    fun launch(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        return true
    }

    fun installed(context: Context, pkg: String) = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }

    /** Your own apps go first until you rearrange. */
    private val PREFERRED = listOf("com.relli.reels", "com.relli.radio")
    /** The stock home screen and setup screens don't belong in the app row. */
    private val NEVER_SHOW = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith", "com.google.android.tvlauncher")
}
