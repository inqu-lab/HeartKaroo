package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.testutil.RecordingEmitter
import com.inqulab.heartkaroo.testutil.streamPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WPrimeBalanceDataTypeWiringTest {

    @Test
    fun `W prime balance streams from the first sample`() = runTest {
        val em = RecordingEmitter()
        WPrimeBalanceDataType(
            "e",
            listOf(streamPoint(150.0)).asFlow(),
            { 250 },
            { 20_000 },
            UnconfinedTestDispatcher(testScheduler),
        ).startStream(em)
        runCurrent()
        assertTrue(em.last() is StreamState.Streaming)
    }
}
