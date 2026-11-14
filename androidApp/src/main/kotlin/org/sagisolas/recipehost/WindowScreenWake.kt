/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost

import android.app.Activity
import android.view.WindowManager
import org.sagisolas.recipehost.platform.ScreenWake

/**
 * Keeps the screen on with [activity]'s window flag. The flag only holds while the window is
 * visible, so it needs no permission and lets go by itself when the app leaves the screen.
 */
internal class WindowScreenWake(
    private val activity: Activity,
) : ScreenWake {
    override fun keepOn(on: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
    }
}
