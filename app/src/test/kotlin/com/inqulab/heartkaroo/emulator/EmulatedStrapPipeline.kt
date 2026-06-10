package com.inqulab.heartkaroo.emulator

import com.inqulab.heartkaroo.hrv.DfaAlpha1Calculator
import com.inqulab.heartkaroo.hrv.EctopicDetector
import com.inqulab.heartkaroo.hrv.HRVCalculator
import com.inqulab.heartkaroo.hrv.HRVStressCalculator
import com.inqulab.heartkaroo.hrv.Pnn50Calculator
import com.inqulab.heartkaroo.hrv.PoincareCalculator
import com.inqulab.heartkaroo.hrv.RespiratoryRateCalculator
import com.inqulab.heartkaroo.hrv.RrArtifactCorrector
import com.inqulab.heartkaroo.hrv.SdnnCalculator

/**
 * The HRV wiring of PolarBleManager.consumeHrData, fed from emulated RR
 * intervals instead of the Polar SDK: the raw stream drives the ectopic
 * counter and respiratory rate, the artifact-corrected stream drives the
 * variability metrics, and DFA α1 is withheld while the recent artifact
 * rate is too high — the same gates the live strap path applies.
 *
 * The stress calculator's EMA baseline and warmup gate are wall-clock based,
 * so it is driven with the simulated ride time rather than the real clock.
 */
class EmulatedStrapPipeline {
    private var nowMs = 0L
    private val rmssdCalc = HRVCalculator(windowSize = 30)
    private val stressCalc = HRVStressCalculator(clockMs = { nowMs })
    private val dfaCalc = DfaAlpha1Calculator()
    private val artifactCorrector = RrArtifactCorrector()
    private val sdnnCalc = SdnnCalculator()
    private val pnn50Calc = Pnn50Calculator()
    private val poincareCalc = PoincareCalculator()
    private val respCalc = RespiratoryRateCalculator()
    private val ectopicCalc = EctopicDetector()

    var rmssd: Float? = null; private set
    var stressPct: Float? = null; private set
    var dfaAlpha1: Float? = null; private set
    var sdnn: Float? = null; private set
    var pnn50: Float? = null; private set
    var sd1: Float? = null; private set
    var sd2: Float? = null; private set
    var sd1Sd2Ratio: Float? = null; private set
    var respiratoryRate: Float? = null; private set
    var ectopicRate: Float? = null; private set

    private companion object {
        // Mirrors PolarBleManager.MAX_ALPHA1_ARTIFACT_RATE.
        const val MAX_ALPHA1_ARTIFACT_RATE = 0.05
    }

    /** Feed one strap notification's worth of RR intervals (a [KarooEmulator.Tick]). */
    fun onRrIntervals(tMs: Long, rrsMs: List<Int>) {
        nowMs = tMs
        for (rr in rrsMs) {
            if (rr <= 0) continue
            // Raw stream: ectopic count and respiratory rate.
            ectopicCalc.addInterval(rr)
            respCalc.addInterval(rr)
            // Artifact-corrected stream: the variability metrics.
            artifactCorrector.accept(rr)?.let { clean ->
                rmssdCalc.addInterval(clean)
                dfaCalc.addInterval(clean)
                sdnnCalc.addInterval(clean)
                pnn50Calc.addInterval(clean)
                poincareCalc.addInterval(clean)
            }
        }
        if (rrsMs.isEmpty()) return
        if (rmssdCalc.hasData) {
            val r = rmssdCalc.getRmssd()
            rmssd = r
            stressCalc.addRmssd(r)
            stressPct = stressCalc.getStressPct()
        }
        dfaAlpha1 =
            if (artifactCorrector.recentArtifactRate() > MAX_ALPHA1_ARTIFACT_RATE) null
            else dfaCalc.getAlpha1()
        sdnn = sdnnCalc.getSdnn()
        pnn50 = pnn50Calc.getPnn50()
        val pc = poincareCalc.getResult()
        sd1 = pc?.sd1
        sd2 = pc?.sd2
        sd1Sd2Ratio = pc?.ratio
        respiratoryRate = respCalc.getBreathsPerMin()
        ectopicRate = ectopicCalc.getEventsPerMin()
    }
}
