package tk.glucodata.drivers.aidex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tk.glucodata.drivers.aidex.`native`.crypto.AesCfb128
import tk.glucodata.drivers.aidex.`native`.crypto.Crc16CcittFalse
import tk.glucodata.drivers.aidex.`native`.crypto.Crc8Maxim

/**
 * Pure JVM tests for the AiDex crypto/CRC primitives. These are the algorithms
 * the driver relies on to talk to the sensor, so they are worth pinning even
 * though the end-to-end BLE path needs hardware.
 */
class AiDexCryptoTests {

    // --- CRC-16/CCITT-FALSE ---
    // Ground-truth single-byte vectors documented in Crc16CcittFalse.kt, taken
    // from real sensor command frames.

    @Test
    fun crc16_matchesDocumentedSensorVectors() {
        assertEquals(0xF3C1, Crc16CcittFalse.checksum(byteArrayOf(0x10)))
        assertEquals(0xE3E0, Crc16CcittFalse.checksum(byteArrayOf(0x11)))
        assertEquals(0xD5B3, Crc16CcittFalse.checksum(byteArrayOf(0x21)))
        assertEquals(0x2EAD, Crc16CcittFalse.checksum(byteArrayOf(0xF2.toByte())))
    }

    @Test
    fun crc16_matchesStandardCheckValue() {
        // Canonical CRC-16/CCITT-FALSE check over ASCII "123456789" is 0x29B1.
        assertEquals(0x29B1, Crc16CcittFalse.checksum("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun crc16_makeCommandAppendsLittleEndianTrailer() {
        // opcode 0x10 -> CRC 0xF3C1 -> frame [0x10, 0xC1, 0xF3]
        assertArrayEquals(
            byteArrayOf(0x10, 0xC1.toByte(), 0xF3.toByte()),
            Crc16CcittFalse.makeCommand(0x10),
        )
    }

    @Test
    fun crc16_makeCommandWithU16LaysOutParamLittleEndian() {
        val frame = Crc16CcittFalse.makeCommandWithU16(0x20, 0x1234)
        assertEquals(0x20.toByte(), frame[0])
        assertEquals(0x34.toByte(), frame[1]) // param low
        assertEquals(0x12.toByte(), frame[2]) // param high
        assertTrue(Crc16CcittFalse.validateResponse(frame))
    }

    @Test
    fun crc16_validateResponseDetectsCorruption() {
        val frame = Crc16CcittFalse.makeCommand(0x11, 0x01, 0x02)
        assertTrue(Crc16CcittFalse.validateResponse(frame))
        val corrupted = frame.copyOf()
        corrupted[1] = (corrupted[1] + 1).toByte()
        assertFalse(Crc16CcittFalse.validateResponse(corrupted))
        assertFalse(Crc16CcittFalse.validateResponse(byteArrayOf(0x01))) // too short
    }

    // --- CRC-8/MAXIM ---

    @Test
    fun crc8_matchesStandardCheckValue() {
        // Canonical CRC-8/MAXIM check over ASCII "123456789" is 0xA1.
        assertEquals(0xA1, Crc8Maxim.checksum("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun crc8_emptyInputIsZeroAndDeterministic() {
        assertEquals(0, Crc8Maxim.checksum(ByteArray(0)))
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals(Crc8Maxim.checksum(data), Crc8Maxim.checksum(data.copyOf()))
    }

    // --- AES-128-CFB ---

    private val key = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(16) { (it * 7 + 1).toByte() }

    @Test
    fun aesCfb_roundTripsBlockAlignedAndPartial() {
        for (len in intArrayOf(16, 20, 33)) {
            val plaintext = ByteArray(len) { (it * 3 + 5).toByte() }
            val ct = AesCfb128.encrypt(plaintext, key, iv)!!
            assertEquals(len, ct.size)
            val back = AesCfb128.decrypt(ct, key, iv)!!
            assertArrayEquals("len=$len", plaintext, back)
        }
    }

    @Test
    fun aesCfb_rejectsWrongSizedKeyOrIv() {
        val pt = ByteArray(16)
        assertNull(AesCfb128.encrypt(pt, ByteArray(8), iv))
        assertNull(AesCfb128.encrypt(pt, key, ByteArray(15)))
        assertNull(AesCfb128.encrypt(ByteArray(0), key, iv))
    }

    @Test
    fun aesCfb_decryptBondDataRecoversSessionKey() {
        // Build a valid 17-byte bond blob: session key (16) + CRC-8/MAXIM(sessionKey),
        // encrypted with the pair key + IV, then decrypt it back through the driver path.
        val sessionKey = ByteArray(16) { (0xA0 + it).toByte() }
        val crc = Crc8Maxim.checksum(sessionKey)
        val plain = sessionKey + byteArrayOf(crc.toByte())
        val bond = AesCfb128.encrypt(plain, key, iv)!!
        assertEquals(17, bond.size)
        val recovered = AesCfb128.decryptBondData(bond, key, iv)
        assertArrayEquals(sessionKey, recovered)
    }

    @Test
    fun aesCfb_decryptBondDataRejectsBadChecksum() {
        val bond = ByteArray(17) { it.toByte() } // arbitrary -> CRC won't match
        assertNull(AesCfb128.decryptBondData(bond, key, iv))
    }
}
