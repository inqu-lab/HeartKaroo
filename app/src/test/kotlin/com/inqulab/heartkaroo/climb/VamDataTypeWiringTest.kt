package com.inqulab.heartkaroo.climb

import com.inqulab.heartkaroo.testutil.RecordingEmitter
import com.inqulab.heartkaroo.testutil.streamPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VamDataTypeWiringTest {

    @Test
    fun `VAM reads Searching until the elevation window fills`() = runTest {
        val em = RecordingEmitter()
        VamDataType("e", listOf(streamPoint(100.0)).asFlow(), UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }
}
