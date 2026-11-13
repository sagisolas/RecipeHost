/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.gradle

private val semver = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

/**
 * The store build number for a plain `MAJOR.MINOR.PATCH` version: Android's `versionCode` and iOS's
 * `CURRENT_PROJECT_VERSION`. It is `major * 1_000_000 + minor * 1_000 + patch` (`1.2.3` -> `1002003`), so it
 * rises with every version bump. Pre-release and build suffixes are rejected: App Store versions can't carry them.
 */
fun buildNumber(version: String): Int {
    val match =
        requireNotNull(semver.matchEntire(version)) {
            "app_version must be MAJOR.MINOR.PATCH (e.g. 1.2.3), was \"$version\""
        }
    val (major, minor, patch) = match.destructured.toList().map { it.toLong() }
    require(minor < 1_000 && patch < 1_000) { "app_version minor and patch must be below 1000, was \"$version\"" }
    val number = major * 1_000_000 + minor * 1_000 + patch
    // Google Play's versionCode ceiling.
    require(number <= 2_100_000_000) { "app_version major must be at most 2099, was \"$version\"" }
    return number.toInt()
}
