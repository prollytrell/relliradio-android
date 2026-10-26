package com.relli.radio.ui

import com.relli.radio.RelliApp
import com.relli.radio.data.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeping the library fresh.
 *
 * Navidrome watches its music folder and scans new files about 5 seconds after they land.
 * The app then needs to forget what it remembered: [refresh] clears the short-term cache and bumps
 * [version], which makes every open screen load again. [sync] also asks Navidrome to scan first.
 */
object Library {
    private val _version = MutableStateFlow(0)
    /** Changes whenever the library should be loaded again. Screens use it as a reload key. */
    val version: StateFlow<Int> get() = _version
    private var lastRefresh = System.currentTimeMillis()

    fun refresh() {
        RelliApp.api.clearCache()
        lastRefresh = System.currentTimeMillis()
        _version.value++
    }

    /** When you come back to the app after a while, show anything that's new. */
    fun refreshIfStale(maxAgeMs: Long = 5 * 60_000L) {
        if (System.currentTimeMillis() - lastRefresh > maxAgeMs) refresh()
    }

    sealed interface SyncResult {
        data class Done(val songs: Int, val scanned: Boolean) : SyncResult
        data class Failed(val message: String) : SyncResult
    }

    /** "Sync library": ask Navidrome to scan, wait for it to finish (up to 3 minutes), then reload. */
    suspend fun sync(onProgress: (String) -> Unit = {}): SyncResult {
        val api = RelliApp.api
        var scanned = true
        try {
            onProgress("Asking Navidrome to scan…")
            var s = api.startScan()
            val started = System.currentTimeMillis()
            while (s.scanning && System.currentTimeMillis() - started < 180_000) {
                onProgress(if (s.count > 0) "Scanning… ${s.count} songs" else "Scanning…")
                delay(1_000)
                s = api.scanStatus()
            }
        } catch (e: ApiException) {
            // Only admin accounts may start a scan. Navidrome scans by itself anyway, so just reload.
            if (e.code == 50 || e.message?.contains("admin", ignoreCase = true) == true || e.message?.contains("authorized", ignoreCase = true) == true) scanned = false
            else return SyncResult.Failed(e.message ?: "Couldn't reach the server")
        } catch (e: Exception) {
            return SyncResult.Failed(e.message ?: "Couldn't reach the server")
        }
        refresh()
        val count = runCatching { api.scanStatus().count }.getOrDefault(0)
        return SyncResult.Done(count, scanned)
    }
}
