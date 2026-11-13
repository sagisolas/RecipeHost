/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Passes the first value at once, then at most one value per [period]: the latest, so the last one
 * always gets through. For live Catalog queries during a Scan, whose batches change the tables many
 * times a second: the query re-runs at most once per [period] instead of once per batch.
 */
internal fun <T> Flow<T>.throttleLatest(period: Duration): Flow<T> =
    conflate().transform {
        emit(it)
        delay(period)
    }

/** How often a live Catalog query re-runs at most while the Catalog keeps changing. */
internal val CATALOG_REFRESH_PERIOD: Duration = 300.milliseconds
