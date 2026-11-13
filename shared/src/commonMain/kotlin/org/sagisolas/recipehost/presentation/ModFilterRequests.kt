/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.presentation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Carries "filter the grid by this mod" from the mod list to the grid. The grid owns its search
 * text and applies each request itself; the mod list only asks. One consumer: the Items tab's grid.
 */
internal class ModFilterRequests {
    /** Conflated: only the latest choice matters if the grid hasn't taken the last one yet. */
    private val channel = Channel<String>(Channel.CONFLATED)

    /** The mod ids asked for, each delivered once. */
    val requests: Flow<String> = channel.receiveAsFlow()

    fun request(modId: String) {
        channel.trySend(modId)
    }
}
