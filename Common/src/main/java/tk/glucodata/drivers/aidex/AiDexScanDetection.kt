package tk.glucodata.drivers.aidex

import java.util.UUID

/**
 * Pure (Android-free) AiDex BLE advertisement detection.
 *
 * This is the scan-time logic that decides whether a discovered BLE device is
 * an AiDex/iCan sensor and what canonical "X-...." serial it maps to. It was
 * originally embedded as private helpers in the mobile AiDexSetupWizard; it is
 * lifted here so it can be shared by a watch pairing entry point and covered by
 * JVM unit tests (it depends only on java.util.UUID / ByteArray / Regex).
 *
 * The mobile wizard can be migrated onto this later; for now it keeps its own
 * copy so the phone build is untouched.
 */
object AiDexScanDetection {

    val CGM_SERVICE_UUID: UUID = UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb")
    val VENDOR_SERVICE_UUID: UUID = UUID.fromString("0000f000-0000-1000-8000-00805f9b34fb")
    val FF30_SERVICE_UUID: UUID = UUID.fromString("0000ff30-0000-1000-8000-00805f9b34fb")

    data class Detection(
        val displayName: String,
        val selectionName: String,
        val serial: String?,
        val isLikelyAiDex: Boolean,
        val detectedViaFf30: Boolean,
    )

    /** Map an advertised name to the canonical internal serial "X-XXXXXXXX", or null. */
    fun normalizeSerial(rawName: String): String? {
        val xPrefixed = Regex("X\\s*-?\\s*([A-Z0-9]{8,})", RegexOption.IGNORE_CASE)
        xPrefixed.find(rawName)?.let { return "X-${it.groupValues[1].uppercase()}" }

        // Some AiDex family sensors advertise a product prefix (e.g. "Vista-...")
        // instead of the canonical "X-..." serial format used internally.
        val familyPrefixed = Regex("(?:AIDEX|LINX|LUMI|VISTA)\\s*[-_]?\\s*([A-Z0-9]{8,})", RegexOption.IGNORE_CASE)
        familyPrefixed.find(rawName)?.let { return "X-${it.groupValues[1].uppercase()}" }

        val cleaned = rawName.trim().replace(" ", "")
        if (cleaned.length == 11 && cleaned.all { it.isLetterOrDigit() }) {
            return "X-${cleaned.uppercase()}"
        }
        return null
    }

    fun looksLikeAiDexFamilyName(rawName: String): Boolean {
        val lowered = rawName.lowercase()
        return lowered.contains("aidex") ||
            lowered.contains("linx") ||
            lowered.contains("lumi") ||
            lowered.contains("vista")
    }

    /** Deterministic fallback serial derived from the BLE MAC when no name is advertised. */
    fun fallbackSerial(address: String): String {
        val body = address.filter(Char::isLetterOrDigit).uppercase()
        return "X-$body"
    }

    fun detect(
        address: String,
        deviceName: String?,
        scanRecordName: String?,
        scanRecordBytes: ByteArray?,
        advertisedServiceUuids: List<UUID>?,
    ): Detection {
        val localName = extractLocalName(scanRecordBytes)
        val names = linkedSetOf<String>()
        listOf(deviceName, scanRecordName, localName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .forEach { names.add(it) }

        val serial = names.firstNotNullOfOrNull { normalizeSerial(it) }
        val nameLooksAiDex = names.any(::looksLikeAiDexFamilyName)
        val hasFf30 = advertisedServiceUuids?.contains(FF30_SERVICE_UUID) == true ||
            advertises16BitService(scanRecordBytes, 0xFF30)
        val hasPrimaryServiceHint =
            advertisedServiceUuids?.any { it == CGM_SERVICE_UUID || it == VENDOR_SERVICE_UUID } == true ||
                advertises16BitService(scanRecordBytes, 0x181F) ||
                advertises16BitService(scanRecordBytes, 0xF000)
        val isLikelyAiDex = serial != null || nameLooksAiDex || hasFf30 || hasPrimaryServiceHint
        return Detection(
            displayName = names.firstOrNull() ?: address,
            selectionName = serial ?: fallbackSerial(address),
            serial = serial,
            isLikelyAiDex = isLikelyAiDex,
            detectedViaFf30 = hasFf30,
        )
    }

    /** Extract the Complete/Shortened Local Name (AD types 0x08/0x09) from raw scan-record bytes. */
    fun extractLocalName(scanRecord: ByteArray?): String? {
        if (scanRecord == null) return null
        var offset = 0
        while (offset < scanRecord.size - 1) {
            val len = scanRecord[offset].toInt() and 0xFF
            if (len == 0) break
            val next = offset + len + 1
            if (next > scanRecord.size) break
            val type = scanRecord[offset + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                val start = offset + 2
                if (next > start) {
                    return try {
                        String(scanRecord, start, next - start, Charsets.UTF_8)
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            offset = next
        }
        return null
    }

    /** True if the raw scan record advertises the given 16-bit service UUID (AD types 0x02/0x03). */
    fun advertises16BitService(scanRecord: ByteArray?, serviceShortUuid: Int): Boolean {
        if (scanRecord == null) return false
        var offset = 0
        while (offset < scanRecord.size - 1) {
            val len = scanRecord[offset].toInt() and 0xFF
            if (len == 0) break
            val next = offset + len + 1
            if (next > scanRecord.size) break
            val type = scanRecord[offset + 1].toInt() and 0xFF
            if (type == 0x02 || type == 0x03) {
                var uuidOffset = offset + 2
                while (uuidOffset + 1 < next) {
                    val uuid = (scanRecord[uuidOffset].toInt() and 0xFF) or
                        ((scanRecord[uuidOffset + 1].toInt() and 0xFF) shl 8)
                    if (uuid == serviceShortUuid) return true
                    uuidOffset += 2
                }
            }
            offset = next
        }
        return false
    }
}
