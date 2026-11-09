package org.sagisolas.recipehost.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleLatestTest {

    @Test
    fun theFirstValueGoesThroughAtOnceAndABurstAfterItOnlyAsItsLatest() = runTest {
        val upstream = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val seen = mutableListOf<Int>()
        backgroundScope.launch { upstream.throttleLatest(300.milliseconds).collect { seen += it } }
        runCurrent()

        upstream.emit(1)
        runCurrent()
        assertEquals(listOf(1), seen)

        upstream.emit(2)
        upstream.emit(3)
        upstream.emit(4)
        runCurrent()
        assertEquals(listOf(1), seen, "a burst within the period waits")

        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf(1, 4), seen, "then only its latest value goes through")
    }
}
