/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.platform

import platform.UIKit.UIApplication

/** Keeps the screen on by turning off the app's idle timer, so the device doesn't auto-lock. */
internal class IdleTimerScreenWake : ScreenWake {
    override fun keepOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }
}
