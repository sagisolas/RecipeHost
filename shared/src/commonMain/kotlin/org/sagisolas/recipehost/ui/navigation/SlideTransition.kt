/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * The transition a tab's `NavDisplay` drives by the back gesture's progress (Android's predictive
 * back, iOS's edge swipe): [slideOff], linear, so the progress maps straight to the finger's
 * position. Only the easing counts here: `NavDisplay` positions the screens by progress, not time.
 */
internal fun predictivePopTransition(): ContentTransform = slideOff(tween(easing = LinearEasing))

/** A pop by the back button: [slideOff], quickly. */
internal fun popTransition(): ContentTransform = slideOff(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing))

/**
 * Opening a screen: the mirror of [slideOff]. The new screen slides in from the right over the one
 * below, which moves a third of the width to the left.
 */
internal fun pushTransition(): ContentTransform {
    val spec = tween<IntOffset>(SLIDE_MILLIS, easing = FastOutSlowInEasing)
    return slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 }
}

/** The screen being left slides off to the right, over the one below, which comes in from a third of the width to the left. */
private fun slideOff(spec: FiniteAnimationSpec<IntOffset>): ContentTransform =
    (
        slideInHorizontally(spec) { -it / 3 } togetherWith
            slideOutHorizontally(spec) { it }
    ).apply {
        // The leaving screen stays on top while it slides.
        targetContentZIndex = -1f
    }

private const val SLIDE_MILLIS = 200
