package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.testutil.RecordingEmitter
import com.inqulab.heartkaroo.testutil.firstValue
import com.inqulab.heartkaroo.testutil.streamPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerDataTypeWiringTest {

    private fun shared() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)
    private fun powerPoints(n: Int, watts: Double) = List(n) { streamPoint(watts) }.asFlow()

    @Test
    fun `VI streams ~1 for steady power once NP is available`() = runTest {
        val em = RecordingEmitter()
        VariabilityIndexDataType("e", powerPoints(31, 200.0), UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertTrue(em.last() is StreamState.Streaming)
        assertEquals(1.0, em.last().firstValue(), 0.05)
    }

    @Test
    fun `IF streams NP over FTP`() = runTest {
        val em = RecordingEmitter()
        IntensityFactorDataType("e", powerPoints(31, 270.0), { 270 }, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertTrue(em.last() is StreamState.Streaming)
        assertEquals(1.0, em.last().firstValue(), 0.05)
    }

    @Test
    fun `TSS streams once NP is available`() = runTest {
        val em = RecordingEmitter()
        TssDataType("e", powerPoints(31, 200.0), { 270 }, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertTrue(em.last() is StreamState.Streaming)
    }

    @Test
    fun `kilojoules streams from the first sample`() = runTest {
        val em = RecordingEmitter()
        KilojoulesDataType("e", listOf(streamPoint(200.0)).asFlow(), UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertTrue(em.last() is StreamState.Streaming)
    }

    @Test
    fun `coasting reads Searching during warmup`() = runTest {
        val em = RecordingEmitter()
        CoastingDataType("e", listOf(streamPoint(200.0)).asFlow(), UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }

    @Test
    fun `mmp reads Searching until its window fills`() = runTest {
        val em = RecordingEmitter()
        MmpDataType("e", "mmp_5s", 5_000L, listOf(streamPoint(200.0)).asFlow(), UnconfinedTestDispatcher(testScheduler)).startStream(em)
        runCurrent()
        assertEquals(StreamState.Searching, em.last())
    }

    @Test
    fun `quadrant streams the dominant quadrant 1 to 4`() = runTest {
        val power = shared()
        val cadence = shared()
        val em = RecordingEmitter()
        QuadrantAnalysisDataType("e", power, cadence, { 270 }, UnconfinedTestDispatcher(testScheduler)).startStream(em)
        power.tryEmit(streamPoint(200.0)); cadence.tryEmit(streamPoint(90.0)); runCurrent()
        assertTrue(em.last() is StreamState.Streaming)
        val q = em.last().firstValue()
        assertTrue("quadrant should be 1..4 but was $q", q in 1.0..4.0)
    }
}
