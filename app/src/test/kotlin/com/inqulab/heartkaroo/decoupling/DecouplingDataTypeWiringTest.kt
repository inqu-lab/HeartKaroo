package com.inqulab.heartkaroo.decoupling

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
class DecouplingDataTypeWiringTest {

    private fun shared() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)

    @Test
    fun `Pw Hr decoupling reads Searching during warmup`() = runTest {
        val power = shared(); val hr = shared(); val em = RecordingEmitter()
        DecouplingDataType("e", power, hr, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); hr.tryEmit(streamPoint(100.0)); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }

    @Test
    fun `Pa Hr decoupling reads Searching during warmup`() = runTest {
        val speed = shared(); val hr = shared(); val em = RecordingEmitter()
        PaHrDecouplingDataType("e", speed, hr, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        speed.tryEmit(streamPoint(8.0)); hr.tryEmit(streamPoint(100.0)); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }

    @Test
    fun `cardiac pop reads Searching before the pop`() = runTest {
        val power = shared(); val hr = shared(); val em = RecordingEmitter()
        CardiacPopDataType("e", power, hr, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); hr.tryEmit(streamPoint(100.0)); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }
}
