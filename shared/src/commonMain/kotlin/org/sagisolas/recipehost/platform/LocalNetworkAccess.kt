/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.platform

/**
 * Whether the OS keeps this app off the local network, supplied by the Android or iOS wrapper.
 * iOS gates LAN connections behind its Local Network permission; Android never does.
 */
fun interface LocalNetworkAccess {
    /**
     * True if the OS denies this app connections to [host], so only the permission in Settings
     * helps, not a Wi-Fi fix. The permission is per app; [host] is what the OS checks it against.
     */
    suspend fun isDenied(host: String): Boolean
}
