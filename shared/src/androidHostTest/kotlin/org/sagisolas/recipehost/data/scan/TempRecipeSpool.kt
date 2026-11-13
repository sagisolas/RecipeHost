/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.scan

import okio.FileSystem
import okio.Path
import kotlin.random.Random

/** A spool file of its own in the JVM's temp directory, so test classes running in parallel never share one. */
internal fun tempSpoolPath(): Path =
    FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "recipehost-test-${Random.nextLong().toULong()}.spool"

internal fun tempRecipeSpool(path: Path = tempSpoolPath()) = RecipeSpool(FileSystem.SYSTEM, path)
