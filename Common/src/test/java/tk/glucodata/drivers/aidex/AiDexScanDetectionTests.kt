package tk.glucodata.drivers.aidex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDexScanDetectionTests {

    @Test
    fun normalizeSerial_acceptsXPrefixedName() {
        assertEquals("X-AB12CD34", AiDexScanDetection.normalizeSerial("X-AB12CD34"))
        assertEquals("X-AB12CD34", AiDexScanDetection.normalizeSerial("X AB12CD34"))
        assertEquals("X-AB12CD34", AiDexScanDetection.normalizeSerial("x-ab12cd34"))
    }

    @Test
    fun normalizeSerial_acceptsFamilyPrefixedNames() {
        assertEquals("X-12345678", AiDexScanDetection.normalizeSerial("Vista-12345678"))
        assertEquals("X-12345678", AiDexScanDetection.normalizeSerial("AiDEX_12345678"))
        assertEquals("X-12345678", AiDexScanDetection.normalizeSerial("LinX 12345678"))
    }

    @Test
    fun normalizeSerial_acceptsBareElevenCharToken() {
        assertEquals("X-ABCDEFGHIJK", AiDexScanDetection.normalizeSerial("ABCDEFGHIJK"))
    }

    @Test
    fun normalizeSerial_rejectsUnrelatedNames() {
        assertNull(AiDexScanDetection.normalizeSerial("SomeWatch"))
        assertNull(AiDexScanDetection.normalizeSerial(""))
        assertNull(AiDexScanDetection.normalizeSerial("X-SHORT"))
    }

    @Test
    fun fallbackSerial_isDeterministicFromAddress() {
        assertEquals("X-AABBCCDDEEFF", AiDexScanDetection.fallbackSerial("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun familyNameMatchIsCaseInsensitive() {
        assertTrue(AiDexScanDetection.looksLikeAiDexFamilyName("My AIDEX sensor"))
        assertTrue(AiDexScanDetection.looksLikeAiDexFamilyName("vista"))
        assertFalse(AiDexScanDetection.looksLikeAiDexFamilyName("Dexcom G7"))
    }

    @Test
    fun extractLocalName_readsCompleteLocalNameAdStructure() {
        val name = "X-AB12CD34"
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val record = ByteArray(nameBytes.size + 2)
        record[0] = (nameBytes.size + 1).toByte() // length: type + name
        record[1] = 0x09                           // Complete Local Name
        System.arraycopy(nameBytes, 0, record, 2, nameBytes.size)
        assertEquals(name, AiDexScanDetection.extractLocalName(record))
    }

    @Test
    fun advertises16BitService_findsFf30InCompleteList() {
        // AD: len=0x03, type=0x03 (complete list of 16-bit UUIDs), 0x30 0xFF -> 0xFF30
        val record = byteArrayOf(0x03, 0x03, 0x30, 0xFF.toByte())
        assertTrue(AiDexScanDetection.advertises16BitService(record, 0xFF30))
        assertFalse(AiDexScanDetection.advertises16BitService(record, 0x181F))
    }

    @Test
    fun detect_identifiesAiDexByName() {
        val d = AiDexScanDetection.detect(
            address = "AA:BB:CC:DD:EE:FF",
            deviceName = "X-AB12CD34",
            scanRecordName = null,
            scanRecordBytes = null,
            advertisedServiceUuids = null,
        )
        assertTrue(d.isLikelyAiDex)
        assertEquals("X-AB12CD34", d.serial)
        assertEquals("X-AB12CD34", d.selectionName)
    }

    @Test
    fun detect_identifiesAiDexByFf30ServiceWhenUnnamed() {
        val d = AiDexScanDetection.detect(
            address = "AA:BB:CC:DD:EE:FF",
            deviceName = null,
            scanRecordName = null,
            scanRecordBytes = null,
            advertisedServiceUuids = listOf(AiDexScanDetection.FF30_SERVICE_UUID),
        )
        assertTrue(d.isLikelyAiDex)
        assertTrue(d.detectedViaFf30)
        assertNull(d.serial)
        assertEquals("X-AABBCCDDEEFF", d.selectionName) // fallback from MAC
    }

    @Test
    fun detect_ignoresUnrelatedDevice() {
        val d = AiDexScanDetection.detect(
            address = "11:22:33:44:55:66",
            deviceName = "Galaxy Buds",
            scanRecordName = null,
            scanRecordBytes = null,
            advertisedServiceUuids = null,
        )
        assertFalse(d.isLikelyAiDex)
    }

    // --- edge cases ---

    @Test
    fun normalizeSerial_extractsFamilySerialEmbeddedInText() {
        assertEquals("X-ABCDEF12", AiDexScanDetection.normalizeSerial("Sensor Vista-ABCDEF12 tail"))
    }

    @Test
    fun normalizeSerial_bareTokenMustBeExactlyEleven() {
        assertNull(AiDexScanDetection.normalizeSerial("ABCDEFGHIJ"))    // 10
        assertNull(AiDexScanDetection.normalizeSerial("ABCDEFGHIJKL")) // 12
        assertEquals("X-ABCDEFGHIJK", AiDexScanDetection.normalizeSerial("ABCDEFGHIJK")) // 11
    }

    @Test
    fun normalizeSerial_collapsesInternalSpacesForBareToken() {
        assertEquals("X-ABCDEFGHIJK", AiDexScanDetection.normalizeSerial("ABCDE FGHIJK"))
    }

    @Test
    fun normalizeSerial_xPrefixNeedsAtLeastEightBodyChars() {
        assertEquals("X-ABCD1234", AiDexScanDetection.normalizeSerial("X-ABCD1234")) // 8 body
        assertNull(AiDexScanDetection.normalizeSerial("X-ABCD123"))                  // 7 body
    }

    @Test
    fun detect_usesScanRecordNameWhenDeviceNameMissing() {
        val d = AiDexScanDetection.detect(
            address = "AA:BB:CC:DD:EE:FF",
            deviceName = null,
            scanRecordName = "X-DEADBEEF",
            scanRecordBytes = null,
            advertisedServiceUuids = null,
        )
        assertTrue(d.isLikelyAiDex)
        assertEquals("X-DEADBEEF", d.serial)
    }

    @Test
    fun detect_readsSerialFromLocalNameInScanRecord() {
        val name = "X-DEADBEEF"
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val record = ByteArray(nameBytes.size + 2)
        record[0] = (nameBytes.size + 1).toByte()
        record[1] = 0x09
        System.arraycopy(nameBytes, 0, record, 2, nameBytes.size)
        val d = AiDexScanDetection.detect(
            address = "AA:BB:CC:DD:EE:FF",
            deviceName = null,
            scanRecordName = null,
            scanRecordBytes = record,
            advertisedServiceUuids = null,
        )
        assertEquals("X-DEADBEEF", d.serial)
    }

    @Test
    fun detect_flagsPrimaryServiceHintFromRawBytesWithoutFf30() {
        // AD: complete list of 16-bit UUIDs containing 0x181F only
        val record = byteArrayOf(0x03, 0x03, 0x1F, 0x18)
        val d = AiDexScanDetection.detect(
            address = "AA:BB:CC:DD:EE:FF",
            deviceName = null,
            scanRecordName = null,
            scanRecordBytes = record,
            advertisedServiceUuids = null,
        )
        assertTrue(d.isLikelyAiDex)
        assertFalse(d.detectedViaFf30)
        assertNull(d.serial)
    }

    @Test
    fun detect_firstNonBlankNameBecomesDisplayName() {
        val d = AiDexScanDetection.detect(
            address = "AA:BB:CC:DD:EE:FF",
            deviceName = "  ",              // blank -> ignored
            scanRecordName = "X-AB12CD34",
            scanRecordBytes = null,
            advertisedServiceUuids = null,
        )
        assertEquals("X-AB12CD34", d.displayName)
    }

    @Test
    fun extractLocalName_returnsNullOnMalformedRecord() {
        // length byte claims 5 bytes but only 1 follows the type
        assertNull(AiDexScanDetection.extractLocalName(byteArrayOf(0x05, 0x09, 0x41)))
        assertNull(AiDexScanDetection.extractLocalName(ByteArray(0)))
    }

    @Test
    fun advertises16BitService_findsUuidAmongMultiple() {
        // two UUIDs: 0x181F then 0xFF30
        val record = byteArrayOf(0x05, 0x03, 0x1F, 0x18, 0x30, 0xFF.toByte())
        assertTrue(AiDexScanDetection.advertises16BitService(record, 0x181F))
        assertTrue(AiDexScanDetection.advertises16BitService(record, 0xFF30))
        assertFalse(AiDexScanDetection.advertises16BitService(record, 0xF000))
    }
}
