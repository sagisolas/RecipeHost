/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.platform

/**
 * The OS's per-app Local Network permission, supplied by the Android or iOS wrapper. Both gate
 * LAN connections behind it: iOS asks on the app's first LAN connection by itself, Android 17+
 * (`ACCESS_LOCAL_NETWORK`) only when the app [request]s it.
 */
fun interface LocalNetworkAccess {
    /**
     * True if the OS denies this app connections to [host], so only the permission in Settings
     * helps, not a Wi-Fi fix. The permission is per app; [host] is what the OS checks it against.
     */
    suspend fun isDenied(host: String): Boolean

    /**
     * Asks the user for the permission if the OS hasn't settled it yet, and returns once they've
     * answered: false if the app is known to be denied, true otherwise. Called once per `Scan`,
     * before its first connection. By default asks nothing and returns true, for an OS that asks
     * by itself (iOS).
     */
    suspend fun request(): Boolean = true

    /** Opens this app's page in the OS settings, where the user can allow the permission. */
    fun openSettings() {}
}
