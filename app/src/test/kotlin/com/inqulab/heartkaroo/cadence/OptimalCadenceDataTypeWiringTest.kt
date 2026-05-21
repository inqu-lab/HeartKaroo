package com.inqulab.heartkaroo.cadence

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
class OptimalCadenceDataTypeWiringTest {

    private fun shared() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)

    @Test
    fun `optimal cadence reads Searching until two bins are comparable`() = runTest {
        val power = shared(); val hr = shared(); val cadence = shared()
        val em = RecordingEmitter()
        OptimalCadenceDataType("e", power, hr, cadence, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); hr.tryEmit(streamPoint(140.0)); cadence.tryEmit(streamPoint(90.0)); runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }
}
