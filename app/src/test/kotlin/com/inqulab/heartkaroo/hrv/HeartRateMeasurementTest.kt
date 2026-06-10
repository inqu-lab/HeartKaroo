package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateMeasurementTest {

    private fun bytes(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    @Test
    fun `returns null for payloads shorter than 2 bytes`() {
        assertNull(parseHeartRateMeasurement(ByteArray(0)))
        assertNull(parseHeartRateMeasurement(bytes(0x00)))
    }

    @Test
    fun `parses 8-bit HR with no RR intervals`() {
        // flags=0x00 (uint8 HR, no RR), bpm=72
        val parsed = parseHeartRateMeasurement(bytes(0x00, 72))
        assertEquals(72, parsed!!.bpm)
        assertTrue(parsed.rrIntervalsMs.isEmpty())
    }

    @Test
    fun `parses 16-bit HR with no RR intervals`() {
        // flags=0x01, bpm=300 (LE: 0x2C 0x01)
        val parsed = parseHeartRateMeasurement(bytes(0x01, 0x2C, 0x01))
        assertEquals(300, parsed!!.bpm)
        assertTrue(parsed.rrIntervalsMs.isEmpty())
    }

    @Test
    fun `returns null when 16-bit HR is advertised but bytes are missing`() {
        assertNull(parseHeartRateMeasurement(bytes(0x01, 0x2C)))
    }

    @Test
    fun `skips the Energy Expended field before the RR intervals`() {
        // flags=0x18 (uint8 HR + Energy Expended + RR), bpm=60, EE=4660 kJ
        // (LE: 0x34 0x12), RR raw=819 (~799 ms). Without the EE skip the EE
        // bytes were decoded as a bogus first RR and the real RR misread.
        val parsed = parseHeartRateMeasurement(bytes(0x18, 60, 0x34, 0x12, 0x33, 0x03))
        assertEquals(60, parsed!!.bpm)
        assertEquals(listOf(799), parsed.rrIntervalsMs)
    }

    @Test
    fun `skips Energy Expended after a 16-bit HR too`() {
        // flags=0x19 (uint16 HR + EE + RR), bpm=300, EE, RR raw=819
        val parsed = parseHeartRateMeasurement(bytes(0x19, 0x2C, 0x01, 0x34, 0x12, 0x33, 0x03))
        assertEquals(300, parsed!!.bpm)
        assertEquals(listOf(799), parsed.rrIntervalsMs)
    }

    @Test
    fun `parses 8-bit HR with one RR interval converted from 1 over 1024s units`() {
        // flags=0x10 (uint8 HR + RR present), bpm=60, RR raw=819 (~800 ms)
        // 819 * 1000 / 1024 = 799
        val parsed = parseHeartRateMeasurement(bytes(0x10, 60, 0x33, 0x03))
        assertEquals(60, parsed!!.bpm)
        assertEquals(listOf(799), parsed.rrIntervalsMs)
    }

    @Test
    fun `parses 8-bit HR with multiple RR intervals`() {
        // flags=0x10, bpm=70, RRs raw: 1024 (=1000ms), 512 (=500ms), 256 (=250ms)
        val parsed = parseHeartRateMeasurement(
            bytes(0x10, 70, 0x00, 0x04, 0x00, 0x02, 0x00, 0x01)
        )
        assertEquals(70, parsed!!.bpm)
        assertEquals(listOf(1000, 500, 250), parsed.rrIntervalsMs)
    }

    @Test
    fun `parses 16-bit HR with RR intervals`() {
        // flags=0x11, bpm=350 (0x5E 0x01), RR raw=1024 → 1000 ms
        val parsed = parseHeartRateMeasurement(
            bytes(0x11, 0x5E, 0x01, 0x00, 0x04)
        )
        assertEquals(350, parsed!!.bpm)
        assertEquals(listOf(1000), parsed.rrIntervalsMs)
    }

    @Test
    fun `ignores a trailing odd byte in the RR section`() {
        // flags=0x10, bpm=80, one complete RR (raw=1024 → 1000ms), then a stray byte
        val parsed = parseHeartRateMeasurement(
            bytes(0x10, 80, 0x00, 0x04, 0xAA)
        )
        assertEquals(80, parsed!!.bpm)
        assertEquals(listOf(1000), parsed.rrIntervalsMs)
    }

    @Test
    fun `returns empty RR list when RR flag is clear even if extra bytes are present`() {
        // flags=0x00 (no RR), bpm=72, extra bytes should be ignored
        val parsed = parseHeartRateMeasurement(bytes(0x00, 72, 0xFF, 0xFF))
        assertEquals(72, parsed!!.bpm)
        assertTrue(parsed.rrIntervalsMs.isEmpty())
    }
}
