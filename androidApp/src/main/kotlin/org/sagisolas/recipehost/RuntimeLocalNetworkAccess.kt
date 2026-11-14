/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.platform.LocalNetworkAccess

/**
 * [LocalNetworkAccess] on Android 17's `ACCESS_LOCAL_NETWORK` runtime permission. Without it, an
 * app targeting API 37 can't open connections to LAN addresses (while `ping` and `adb shell` still
 * can). Older Android has no such gate.
 *
 * After the user has denied it twice, Android answers a request with "denied" without showing the
 * dialog; [openSettings] is then the only way back.
 *
 * Must be created during [activity]'s `onCreate`: the permission launcher has to be registered
 * before the activity starts.
 */
internal class RuntimeLocalNetworkAccess(
    private val activity: ComponentActivity,
) : LocalNetworkAccess {
    /** One prompt at a time; a second caller waits and then sees the first one's answer. */
    private val prompt = Mutex()

    /** The prompt on screen, if any. Read and written on the main thread only. */
    private var answer: CompletableDeferred<Boolean>? = null

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            answer?.complete(granted)
            answer = null
        }

    override suspend fun isDenied(host: String): Boolean = isGated() && !isGranted()

    override suspend fun request(): Boolean {
        if (!isGated()) return true
        return prompt.withLock {
            if (isGranted()) return@withLock true
            // A cancelled Scan leaves its dialog up: wait for that answer rather than stacking a
            // second launch, whose result the launcher couldn't tell apart from the first.
            val pending =
                withContext(Dispatchers.Main) {
                    answer ?: CompletableDeferred<Boolean>().also {
                        answer = it
                        launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }
                }
            pending.await()
        }
    }

    override fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    private fun isGated() = Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun isGranted() =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
}

/** Android 17, the first release that gates LAN connections behind `ACCESS_LOCAL_NETWORK`. */
private const val LOCAL_NETWORK_PERMISSION_SDK = 37
