package com.qigai.neon.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.qigai.neon.MainViewModel
import com.qigai.neon.oauth.GoogleDriveAuth
import kotlinx.coroutines.launch

/** Starts Google authorization (drive.file). Shows Google's consent UI only when needed. */
@Composable
fun rememberDriveAuthorizer(vm: MainViewModel): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            try {
                vm.onDriveAuthorized(GoogleDriveAuth.resultFromIntent(ctx, res.data).accessToken)
            } catch (e: Exception) {
                vm.onDriveAuthFailed("Google 授权失败：${e.message}")
            }
        } else {
            vm.onDriveAuthFailed("已取消 Google 授权")
        }
    }
    return {
        scope.launch {
            try {
                val r = GoogleDriveAuth.authorize(ctx)
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else vm.onDriveAuthorized(r.accessToken)
            } catch (e: Exception) {
                vm.onDriveAuthFailed("Google 授权不可用：${e.message}")
            }
        }
    }
}
