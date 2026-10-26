package com.relli.reels.link

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.relli.reels.ReelsApp

/**
 * Lets RelliHome (your launcher) use the Jellyfin sign-in from RelliReels, so you sign in once.
 * Protected by a "signature" permission: only apps signed with your own key can read it.
 */
class SessionProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val p = ReelsApp.prefs
        val c = MatrixCursor(arrayOf("server", "userId", "token", "deviceId", "userName"))
        if (p.signedIn) c.addRow(arrayOf(p.server.value, p.userId.value, p.token.value, p.deviceId, p.userName.value))
        return c
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
