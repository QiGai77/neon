package com.qigai.neon.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google authorization for Drive (scope drive.file only).
 *
 * Google "Android" OAuth clients cannot use a loopback redirect and custom URI schemes are disabled by
 * default for them, so Custom-Tabs+PKCE against that client is not viable. Instead we use Google Identity
 * Services' AuthorizationClient, which Google recommends for Android: Play services matches this APK's
 * package (com.qigai.neon) + signing SHA-1 against the Android OAuth client in the Cloud project and
 * returns a short-lived access token. Later calls to authorize() return a fresh token silently (no UI)
 * as long as the grant isn't revoked — that is the "no reconnect" auto-renewal; no refresh token is kept
 * on device.
 */
object GoogleDriveAuth {
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE))).build()

    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    /** Token without UI, or null when the user must (re)grant consent. */
    suspend fun silentToken(context: Context): String? {
        val r = authorize(context)
        return if (r.hasResolution()) null else r.accessToken
    }

    /** Gmail + Calendar + Drive, requested only when the user connects Google Workspace. */
    val WORKSPACE_SCOPES = listOf(
        DRIVE_FILE,
        "https://www.googleapis.com/auth/gmail.modify",
        "https://www.googleapis.com/auth/calendar.events",
    )

    suspend fun authorizeWorkspace(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context)
            .authorize(AuthorizationRequest.builder().setRequestedScopes(WORKSPACE_SCOPES.map { Scope(it) }).build()).await()

    suspend fun silentWorkspaceToken(context: Context): String? = runCatching {
        val r = authorizeWorkspace(context)
        if (r.hasResolution()) null else r.accessToken
    }.getOrNull()

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
}

object CustomTabs {
    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
