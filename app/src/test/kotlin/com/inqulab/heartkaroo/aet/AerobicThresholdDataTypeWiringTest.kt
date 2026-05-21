package com.inqulab.heartkaroo.aet

import com.inqulab.heartkaroo.testutil.RecordingEmitter
import com.inqulab.heartkaroo.testutil.streamPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AerobicThresholdDataTypeWiringTest {

    @Test
    fun `AeT reads Searching until enough alpha-power pairs accumulate`() = runTest {
        val power = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)
        val alpha = MutableSharedFlow<Float?>(replay = 1, extraBufferCapacity = 16)
        val em = RecordingEmitter()
        AerobicThresholdDataType("e", power, alpha, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); runCurrent()
        alpha.tryEmit(0.7f); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }
}
