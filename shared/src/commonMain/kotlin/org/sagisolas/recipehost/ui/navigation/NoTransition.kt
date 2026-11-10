package org.sagisolas.recipehost.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * Swaps one screen for the next at once. The app has no animations, and `NavDisplay` animates by
 * default, so every `NavDisplay` passes this as its transition specs. No size transform either.
 */
internal fun noTransition(): ContentTransform =
    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
