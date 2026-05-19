package com.inqulab.heartkaroo.hrv

/**
 * Parsed contents of a BLE Heart Rate Measurement notification
 * (characteristic 0x2A37, see Bluetooth Heart Rate Service spec).
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val rrIntervalsMs: List<Int>,
)

/**
 * Decode a raw Heart Rate Measurement notification.
 *
 * Returns null if the payload is too short or malformed. RR intervals are
 * converted from the BLE 1/1024 s units into milliseconds.
 */
fun parseHeartRateMeasurement(data: ByteArray): HeartRateMeasurement? {
    if (data.size < 2) return null

    val flags = data[0].toInt() and 0xFF
    val hr16bit = flags and 0x01 != 0
    val rrPresent = flags and 0x10 != 0

    val bpm = if (hr16bit) {
        if (data.size < 3) return null
        ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    } else {
        data[1].toInt() and 0xFF
    }

    val rrs = if (rrPresent) {
        val out = ArrayList<Int>()
        var offset = if (hr16bit) 3 else 2
        while (offset + 1 < data.size) {
            val rrRaw = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
            out += rrRaw * 1000 / 1024
            offset += 2
        }
        out
    } else {
        emptyList()
    }

    return HeartRateMeasurement(bpm, rrs)
}
