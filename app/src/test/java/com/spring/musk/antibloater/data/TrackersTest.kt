package com.spring.musk.antibloater.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TrackersTest {
    /** A dex with only a header and a sorted string table, which is all the scanner reads. */
    private fun dex(vararg strings: String): ByteArray {
        val sorted = strings.sorted()
        val header = 0x70
        val idsOff = header
        val dataOff = idsOff + sorted.size * 4
        val data = ByteArrayOutputStream()
        val offsets = sorted.map { s ->
            val at = dataOff + data.size()
            data.write(s.length) // ULEB128, single byte for short strings
            data.write(s.toByteArray())
            data.write(0)
            at
        }
        val buf = ByteBuffer.allocate(dataOff + data.size()).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x38, sorted.size).putInt(0x3C, idsOff)
        offsets.forEachIndexed { i, off -> buf.putInt(idsOff + i * 4, off) }
        buf.position(dataOff); buf.put(data.toByteArray())
        return buf.array()
    }

    private val sample = dex(
        "<init>", "Landroid/app/Activity;", "Lcom/appsflyer/AppsFlyerLib;", "Lcom/example/Main;",
        "Lcom/google/android/gms/ads/AdView;", "Ljava/lang/String;", "onCreate",
    )

    @Test fun findsPrefixes() {
        assertEquals(true, Trackers.hasPrefix(sample, "Lcom/appsflyer/"))
        assertEquals(true, Trackers.hasPrefix(sample, "Lcom/google/android/gms/ads/"))
    }

    @Test fun ignoresMissingAndPartial() {
        assertEquals(false, Trackers.hasPrefix(sample, "Lcom/adjust/sdk/"))
        assertEquals(false, Trackers.hasPrefix(sample, "Lcom/google/android/gms/analytics/"))
        assertEquals(false, Trackers.hasPrefix(sample, "Lcom/appsflyerx/"))
    }

    @Test fun ignoresBarePrefixStrings() {
        // A scanner's own signature list holds the prefixes as plain strings
        val scanner = dex("Lcom/appsflyer/", "Lcom/adjust/sdk/", "Lcom/example/Main;")
        assertEquals(false, Trackers.hasPrefix(scanner, "Lcom/appsflyer/"))
        assertEquals(false, Trackers.hasPrefix(scanner, "Lcom/adjust/sdk/"))
        val both = dex("Lcom/appsflyer/", "Lcom/appsflyer/AppsFlyerLib;")
        assertEquals(true, Trackers.hasPrefix(both, "Lcom/appsflyer/"))
    }

    @Test fun survivesGarbage() {
        assertEquals(false, Trackers.hasPrefix(ByteArray(10), "Lcom/"))
        assertEquals(false, Trackers.hasPrefix(ByteArray(0x80) { -1 }, "Lcom/"))
    }
}
