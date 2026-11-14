/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.platform

/** Keeps the device's screen from turning off by itself, supplied by the Android or iOS wrapper. */
fun interface ScreenWake {
    /** Keeps the screen on while [on], or lets it turn off again on the OS's timeout. Call from the main thread. */
    fun keepOn(on: Boolean)
}
