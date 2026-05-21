package com.inqulab.heartkaroo.efficiency

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
class EfficiencyDataTypeWiringTest {

    private fun shared() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)

    @Test
    fun `efficiency factor reads Searching until the window fills`() = runTest {
        val power = shared(); val hr = shared(); val em = RecordingEmitter()
        EfficiencyFactorDataType("e", power, hr, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); hr.tryEmit(streamPoint(140.0)); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }

    @Test
    fun `cardiac cost reads Searching until the window fills`() = runTest {
        val power = shared(); val hr = shared(); val em = RecordingEmitter()
        CardiacCostDataType("e", power, hr, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); hr.tryEmit(streamPoint(140.0)); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }
}
