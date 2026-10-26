package com.relli.radio.playback

import com.relli.radio.RelliApp
import com.relli.radio.data.SavedQueue
import com.relli.radio.data.Subsonic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Moving playback between devices (phone, TV, desktop).
 *
 * Every device saves its queue and position to Navidrome while it plays. Navidrome records who saved
 * it ("changedBy") and when ("changed", in the server's clock). Comparing only the server's own
 * timestamps means the devices' clocks never have to agree.
 *
 * - If another device saved after this one did, it's the active one: we offer "Continue from …".
 * - If this device is playing and another one takes over, this one pauses (so the music moves,
 *   instead of playing in two rooms).
 */
object Handoff {
    private val _offer = MutableStateFlow<SavedQueue?>(null)
    /** The other device's queue we can pick up, or null. */
    val offer: StateFlow<SavedQueue?> get() = _offer

    /** Server time of the newest save made by this device that we've seen. */
    @Volatile private var ownAt = 0L
    @Volatile private var dismissedBy: String? = null
    @Volatile private var dismissedUntil = 0L

    /** Set when another device took over; this device then stops saving until you play here again. */
    @Volatile var passive = false
        private set

    /** Asks the server who saved last. Returns true when another device is newer than this one. */
    suspend fun check(): Boolean {
        val q = runCatching { RelliApp.api.playQueue() }.getOrNull() ?: return false
        val by = q.changedBy
        if (by == null || by == Subsonic.CLIENT) {
            ownAt = maxOf(ownAt, q.changedAt)
            _offer.value = null
            return false
        }
        if (q.changedAt <= ownAt) return false
        if (by == dismissedBy && System.currentTimeMillis() < dismissedUntil) return true
        _offer.value = q
        return true
    }

    /** Playback just started here: whatever is on the server now is older than this moment. */
    suspend fun baseline() {
        val q = runCatching { RelliApp.api.playQueue() }.getOrNull() ?: return
        ownAt = maxOf(ownAt, q.changedAt)
    }

    /** Another device took over while this one was playing. */
    fun tookOver() { passive = true }

    /** Playing here again (any play button): this device becomes the active one. */
    fun activeHere() {
        passive = false
        _offer.value?.let { ownAt = maxOf(ownAt, it.changedAt) }
        _offer.value = null
    }

    /** We picked up the other device's queue; anything it saved up to now is old news. */
    fun pickedUp(q: SavedQueue) {
        ownAt = maxOf(ownAt, q.changedAt)
        passive = false
        _offer.value = null
    }

    /** "Not now": stop offering this device for a while. */
    fun dismiss() {
        val q = _offer.value ?: return
        dismissedBy = q.changedBy
        dismissedUntil = System.currentTimeMillis() + 20 * 60_000L
        _offer.value = null
    }
}
