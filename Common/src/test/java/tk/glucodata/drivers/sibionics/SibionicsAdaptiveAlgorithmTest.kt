package tk.glucodata.drivers.sibionics

import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SibionicsAdaptiveAlgorithmTest {
    @Test
    fun exactMeasurementRemainsStableWithoutCalibrationOrNoise() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        val outputs = (1..20).map { index ->
            context.process(6f, 3f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        assertTrue(outputs.all { it == 6f })
    }

    @Test
    fun stockModeUsesIntegratedCalibrationWithoutAdaptiveLag() {
        val algorithm = SibionicsAlgorithmContext("test")
        algorithm.configure(
            shortCode = "46HU804EBJ4",
            sensitivity = 1.4f,
            variant = SibionicsConstants.Variant.CHINESE,
            selection = SibionicsAlgorithmSelection.STOCK_CALIBRATED,
        )
        val anchor = SibionicsCalibrationAnchor(8f, 6f, 30_000L)

        val output = algorithm.process(
            rawMmol = 8f,
            temperatureC = 34f,
            index = 1,
            mode = SibionicsAlgorithmMode.LIVE,
            impedance = 100f,
            eventTimeMs = 60_000L,
            calibrationAnchors = listOf(anchor),
        )

        assertEquals(6.4f, output, 0.001f)
    }

    @Test
    fun plainStockModeIgnoresCalibrationAnchors() {
        val algorithm = SibionicsAlgorithmContext("test")
        algorithm.configure(
            shortCode = "46HU804EBJ4",
            sensitivity = 1.4f,
            variant = SibionicsConstants.Variant.CHINESE,
            selection = SibionicsAlgorithmSelection.STOCK,
        )
        val output = algorithm.process(
            rawMmol = 8f,
            temperatureC = 34f,
            index = 1,
            mode = SibionicsAlgorithmMode.LIVE,
            eventTimeMs = 60_000L,
            calibrationAnchors = listOf(SibionicsCalibrationAnchor(8f, 6f, 30_000L)),
        )

        assertEquals(8f, output, 0.001f)
    }

    @Test
    fun algorithmFeatureStorageRepresentsBothIndependentToggles() {
        assertEquals(SibionicsAlgorithmSelection.STOCK, SibionicsAlgorithmSelection.fromStorage(0))
        assertEquals(SibionicsAlgorithmSelection.STOCK_CALIBRATED, SibionicsAlgorithmSelection.fromStorage(1))
        assertEquals(SibionicsAlgorithmSelection.ADAPTIVE, SibionicsAlgorithmSelection.fromStorage(2))
        assertEquals(SibionicsAlgorithmSelection.ADAPTIVE_CALIBRATED, SibionicsAlgorithmSelection.fromStorage(3))
        assertTrue(!SibionicsAlgorithmSelection.STOCK.calibrationEnabled)
        assertTrue(!SibionicsAlgorithmSelection.STOCK.adaptiveEnabled)
        assertTrue(SibionicsAlgorithmSelection.STOCK_CALIBRATED.calibrationEnabled)
        assertTrue(!SibionicsAlgorithmSelection.STOCK_CALIBRATED.adaptiveEnabled)
        assertTrue(!SibionicsAlgorithmSelection.ADAPTIVE.calibrationEnabled)
        assertTrue(SibionicsAlgorithmSelection.ADAPTIVE.adaptiveEnabled)
        assertTrue(SibionicsAlgorithmSelection.ADAPTIVE_CALIBRATED.calibrationEnabled)
        assertTrue(SibionicsAlgorithmSelection.ADAPTIVE_CALIBRATED.adaptiveEnabled)
    }

    @Test
    fun adaptiveToggleDoesNotImplicitlyEnableCalibration() {
        val anchor = SibionicsCalibrationAnchor(8f, 6f, 30_000L)
        fun firstOutput(selection: SibionicsAlgorithmSelection): Float {
            val algorithm = SibionicsAlgorithmContext("test-${selection.storageId}")
            algorithm.configure("46HU804EBJ4", 1.4f, SibionicsConstants.Variant.CHINESE, selection)
            return algorithm.process(
                rawMmol = 8f,
                temperatureC = 34f,
                index = 1,
                mode = SibionicsAlgorithmMode.LIVE,
                eventTimeMs = 60_000L,
                calibrationAnchors = listOf(anchor),
            )
        }

        assertEquals(8f, firstOutput(SibionicsAlgorithmSelection.ADAPTIVE), 0.001f)
        assertEquals(6.4f, firstOutput(SibionicsAlgorithmSelection.ADAPTIVE_CALIBRATED), 0.001f)
    }

    @Test
    fun preparedCalibrationMeasurementDoesNotReapplyLegacyAnchorMapper() {
        val algorithm = SibionicsAlgorithmContext("prepared")
        algorithm.configure(
            "46HU804EBJ4",
            1.4f,
            SibionicsConstants.Variant.CHINESE,
            SibionicsAlgorithmSelection.STOCK_CALIBRATED,
        )
        val stock = algorithm.processStock(8f, 34f, 1, SibionicsAlgorithmMode.REPLAY)

        val output = algorithm.processPreparedMeasurement(
            stockMmol = stock,
            measurementMmol = 5.7f,
            rawMmol = 8f,
            temperatureC = 34f,
            index = 1,
            impedance = 100f,
            eventTimeMs = 60_000L,
        )

        assertEquals(5.7f, output, 0.001f)
    }

    @Test
    fun calibrationAnchorChangesPersistentSensorStateInsteadOfPostHocOutput() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        val start = context.process(8f, 4f, 34f, 100f, 1, 60_000L, emptyList())
        val anchor = SibionicsCalibrationAnchor(8f, 6f, 90_000L)
        val firstAdjusted = context.process(8f, 4f, 34f, 100f, 2, 120_000L, listOf(anchor))
        val secondAdjusted = context.process(8f, 4f, 34f, 100f, 3, 180_000L, listOf(anchor))

        assertEquals(8f, start, 0.001f)
        assertTrue(firstAdjusted < start)
        assertTrue(secondAdjusted <= firstAdjusted)
        assertTrue(secondAdjusted > 5.5f)
    }

    @Test
    fun telemetryAnomalyCannotMakeTheModelOverrideStockBySixMmol() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(6) { offset ->
            context.process(6f, 3f, 34f, 100f, offset + 1, (offset + 1) * 60_000L, emptyList())
        }
        val anomalous = context.process(12f, 9f, 50f, 10_000f, 7, 420_000L, emptyList())
        assertEquals(12f, anomalous, 0.001f)
    }

    @Test
    fun startupTransientStaysOnTheQrAnchoredStockMeasurement() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        val stock = floatArrayOf(29f, 29.3f, 12.3f, 8.6f, 8f)
        val adaptive = stock.mapIndexed { offset, value ->
            val index = offset + 1
            context.process(value, value, 34f, 100f, index, index * 60_000L, emptyList())
        }

        assertEquals(stock.toList(), adaptive)
    }

    @Test
    fun sustainedCoherentRiseRemainsResponsiveWithoutForecastOvershoot() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(10) { offset ->
            val index = offset + 1
            context.process(6f, 8.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        var stock = 6f
        var raw = 8.4f
        var adaptive = 6f
        repeat(8) { offset ->
            stock += 0.25f
            raw += 0.35f
            val index = 11 + offset
            adaptive = context.process(stock, raw, 34f, 100f, index, index * 60_000L, emptyList())
        }

        assertTrue("stock=$stock adaptive=$adaptive", adaptive >= stock - 0.2f)
        assertTrue("stock=$stock adaptive=$adaptive", adaptive <= stock + 0.1f)
    }

    @Test
    fun alternatingStockNoiseIsNotAmplifiedOrShifted() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(8) { offset ->
            val index = offset + 1
            context.process(6f, 8.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        val stock = floatArrayOf(6.4f, 5.7f, 6.3f, 5.8f, 6.35f, 5.75f, 6.25f, 5.85f)
        val adaptive = stock.mapIndexed { offset, value ->
            val index = offset + 9
            context.process(value, value * 1.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        val stockDeviation = stock.map { abs(it - 6f) }.average()
        val adaptiveDeviation = adaptive.map { abs(it - 6f) }.average()

        assertTrue(
            "stockDeviation=$stockDeviation adaptiveDeviation=$adaptiveDeviation adaptive=$adaptive",
            adaptiveDeviation <= stockDeviation * 1.05,
        )
        assertTrue("adaptive=$adaptive", abs(adaptive.average() - 6.0) < 0.2)
    }

    @Test
    fun oneTenthQuantisationOscillationIsAttenuatedInsteadOfAmplified() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(8) { offset ->
            val index = offset + 1
            context.process(6f, 8.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        val stock = FloatArray(12) { if (it % 2 == 0) 6.1f else 5.9f }
        val adaptive = stock.mapIndexed { offset, value ->
            val index = offset + 9
            context.process(value, value * 1.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        val stockMotion = stock.asList().zipWithNext { before, after -> abs(after - before) }.sum()
        val adaptiveMotion = adaptive.zipWithNext { before, after -> abs(after - before) }.sum()

        assertTrue("stock=$stockMotion adaptive=$adaptiveMotion values=$adaptive", adaptiveMotion < stockMotion)
        assertTrue("adaptive=$adaptive", adaptive.all { abs(it - 6f) <= 0.1f })
    }

    @Test
    fun coherentHighQualityFallIntoLowRangeIsNotHiddenAsAnArtifact() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(8) { offset ->
            val index = offset + 1
            context.process(6.5f, 9.1f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        val falling = floatArrayOf(5.8f, 4.9f, 4.1f, 3.5f, 3.1f)
        val adaptive = falling.mapIndexed { offset, value ->
            val index = offset + 9
            context.process(value, value * 1.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }

        assertTrue("adaptive=$adaptive", adaptive.last() <= 3.6f)
        assertTrue("adaptive=$adaptive", adaptive.last() >= 2.5f)
        assertEquals(falling.last(), adaptive.last(), 0.001f)
    }

    @Test
    fun qrScaledRawTrendOnlyProvidesBoundedDirectionalSupport() {
        val coherent = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        val disagreeing = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(8) { offset ->
            val index = offset + 1
            coherent.process(6f, 8.4f, 34f, 100f, index, index * 60_000L, emptyList())
            disagreeing.process(6f, 8.4f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        var stock = 6f
        var coherentRaw = 8.4f
        var disagreeingRaw = 8.4f
        var coherentOutput = 6f
        var disagreeingOutput = 6f
        repeat(7) { offset ->
            val index = offset + 9
            stock += 0.25f
            coherentRaw += 0.35f
            disagreeingRaw -= 0.12f
            coherentOutput = coherent.process(
                stock, coherentRaw, 34f, 100f, index, index * 60_000L, emptyList(),
            )
            disagreeingOutput = disagreeing.process(
                stock, disagreeingRaw, 34f, 100f, index, index * 60_000L, emptyList(),
            )
        }

        assertTrue("coherent=$coherentOutput stock=$stock", abs(coherentOutput - stock) <= 0.2f)
        assertTrue("disagreeing=$disagreeingOutput stock=$stock", abs(disagreeingOutput - stock) <= 0.2f)
        assertTrue(
            "coherent=$coherentOutput disagreeing=$disagreeingOutput",
            coherentOutput >= disagreeingOutput,
        )
    }

    @Test
    fun invalidMeasurementDoesNotInventOrAdvanceAReading() {
        val context = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        context.process(6f, 8.4f, 34f, 100f, 1, 60_000L, emptyList())

        val missing = context.process(Float.NaN, 8.5f, 34f, 100f, 2, 120_000L, emptyList())
        val recovered = context.process(6.1f, 8.54f, 34f, 100f, 3, 180_000L, emptyList())

        assertTrue(missing.isNaN())
        assertTrue("recovered=$recovered", recovered in 5.8f..6.4f)
    }

    @Test
    fun snapshotRestoresAdaptiveLevelAndTrendState() {
        val original = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        repeat(8) { offset ->
            val index = offset + 1
            original.process(5f + offset * 0.2f, 2.5f + offset * 0.1f, 34f, 100f, index, index * 60_000L, emptyList())
        }
        val restored = SibionicsAdaptiveAlgorithmContext().apply { configure(1.4f) }
        assertTrue(restored.restore(original.snapshot()))

        val expected = original.process(6.8f, 3.4f, 34f, 100f, 9, 540_000L, emptyList())
        val actual = restored.process(6.8f, 3.4f, 34f, 100f, 9, 540_000L, emptyList())
        assertEquals(expected, actual, 0.001f)
    }

    @Test
    fun lifetimesMatchEachSibionicsSeries() {
        val day = SibionicsConstants.DAY_MS
        assertEquals(14L * day, SibionicsConstants.Variant.EU.officialLifetimeMs)
        assertEquals(14L * day, SibionicsConstants.Variant.HEMATONIX.officialLifetimeMs)
        assertEquals(14L * day, SibionicsConstants.Variant.CHINESE.officialLifetimeMs)
        assertEquals(22L * day, SibionicsConstants.Variant.SIBIONICS2.officialLifetimeMs)
        assertEquals(23L * day, SibionicsConstants.Variant.SIBIONICS2.expectedLifetimeMs)
        assertEquals(14L * day, SibionicsConstants.Variant.GS3.officialLifetimeMs)
        assertEquals(14L * day + 36L * 60L * 60L * 1000L, SibionicsConstants.Variant.GS3.expectedLifetimeMs)
    }

    @Test
    fun gs1ResetUsesObservedFf32Payload() {
        assertArrayEquals(
            byteArrayOf(0x24, 0xE7.toByte(), 0x6F, 0x34),
            SibionicsProtocol.buildGs1ResetPacket(),
        )
    }

    @Test
    fun chineseHistoryProgressCountsRecordsRatherThanPacketIndex() {
        val first = chineseEntry(index = 700, unreceived = 12_340)
        assertEquals(12_341, SibionicsProtocol.estimateChineseHistoryTotal(0, 1, listOf(first)))

        val batch = (0 until 10).map { offset ->
            chineseEntry(index = 701 + offset, unreceived = 12_339 - offset)
        }
        assertEquals(12_341, SibionicsProtocol.estimateChineseHistoryTotal(12_341, 11, batch))
    }

    private fun chineseEntry(index: Int, unreceived: Int) = SibionicsProtocol.ChineseEntry(
        index = index,
        rawTemperature = 0,
        rawImpedance = 0,
        rawGlucose = 0,
        status = 0,
        numOfUnreceived = unreceived,
        addTimeSeconds = 0,
    )
}
