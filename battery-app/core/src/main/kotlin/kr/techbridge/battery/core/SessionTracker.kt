package kr.techbridge.battery.core

/**
 * 정규화된 샘플을 순서대로 받아 충전 세션을 나눈다.
 *
 * - 꽂힌 샘플이 들어오면 세션을 시작한다.
 * - 뺀 샘플이 들어오면 세션을 끝낸다(completed = true).
 * - 꽂힌 상태에서 샘플 간격이 [gapToleranceMs]를 넘으면 서비스가 죽었다 살아난 것으로 보고
 *   이전 세션을 completed = false로 끝내고 새 세션을 시작한다.
 * - 서비스가 죽었다 살아났을 때 저장된 미완료 세션을 [resume]으로 이어받을 수 있다.
 *
 * 충전량은 양의 전류를 사다리꼴 적분한다. 전력 평균은 시간 가중 평균이다.
 */
class SessionTracker(
    private val gapToleranceMs: Long = DEFAULT_GAP_TOLERANCE_MS,
) {
    sealed interface Event {
        data class Started(val startMs: Long, val startLevel: Int) : Event
        data class Progress(val snapshot: ChargingSession) : Event
        data class Ended(val session: ChargingSession) : Event
    }

    private var builder: Builder? = null

    /** 진행 중 세션의 현재 스냅숏. 없으면 null. */
    val current: ChargingSession? get() = builder?.snapshot(completed = false)

    fun onSample(s: Sample): List<Event> {
        val out = ArrayList<Event>(2)
        val b = builder

        if (b != null && s.plugged && s.timestampMs - b.lastMs > gapToleranceMs) {
            out += Event.Ended(b.snapshot(completed = false))
            builder = null
        }

        if (s.plugged) {
            val cur = builder ?: Builder.fresh(s).also {
                builder = it
                out += Event.Started(s.timestampMs, s.levelPercent)
            }
            if (cur.lastMs != s.timestampMs) cur.add(s)
            out += Event.Progress(cur.snapshot(completed = false))
        } else if (b != null) {
            // 뺀 순간의 샘플은 전류가 이미 음수라 적분에 넣지 않고 끝 시각과 잔량만 반영한다.
            b.close(s)
            out += Event.Ended(b.snapshot(completed = true))
            builder = null
        }
        return out
    }

    /**
     * 저장된 미완료 세션을 이어받는다. 다음 꽂힌 샘플부터 적분을 계속하므로 Started 이벤트는 나지 않는다.
     * 호출자는 아직 꽂혀 있고 마지막 기록이 [gapToleranceMs] 안쪽일 때만 불러야 한다. 그 밖이면 [onSample]의
     * 간격 검사가 이어받은 세션을 미완료로 끝내고 새 세션을 시작한다.
     */
    fun resume(session: ChargingSession) {
        builder = Builder.from(session)
    }

    /** 외부에서 강제로 끝낼 때(서비스 종료 등). */
    fun flush(): ChargingSession? = builder?.snapshot(completed = false).also { builder = null }

    private class Builder(
        val startMs: Long,
        val startLevel: Int,
        var lastMs: Long,
        var lastLevel: Int,
        /** 직전 샘플의 양의 전류(µA). 이어받은 직후에는 모르므로 0으로 두고 첫 사다리꼴만 절반 가중한다. */
        var lastCurrentUa: Long,
        var lastPowerMw: Double,
        var chargedUah: Double,
        /** 전력 × 시간 적분(mW·ms), 평균용. */
        var powerMwMs: Double,
        /** 적분에 쓴 시간. 뺀 뒤의 꼬리는 포함하지 않는다. */
        var integratedMs: Long,
        var peakPowerMw: Double,
        var peakTempDeciC: Int,
        var count: Int,
    ) {
        fun add(s: Sample) {
            val dtMs = (s.timestampMs - lastMs).coerceAtLeast(0)
            val iUa = s.currentUa.coerceAtLeast(0)
            val pMw = s.powerMw.coerceAtLeast(0.0)
            chargedUah += (lastCurrentUa + iUa) / 2.0 * dtMs / 3_600_000.0
            powerMwMs += (lastPowerMw + pMw) / 2.0 * dtMs
            integratedMs += dtMs
            if (pMw > peakPowerMw) peakPowerMw = pMw
            if (s.temperatureDeciC > peakTempDeciC) peakTempDeciC = s.temperatureDeciC
            lastMs = s.timestampMs
            lastLevel = s.levelPercent
            lastCurrentUa = iUa
            lastPowerMw = pMw
            count++
        }

        fun close(s: Sample) {
            lastMs = s.timestampMs
            lastLevel = s.levelPercent
            count++
        }

        fun snapshot(completed: Boolean): ChargingSession {
            return ChargingSession(
                startMs = startMs,
                endMs = lastMs,
                startLevel = startLevel,
                endLevel = lastLevel,
                chargedMicroAmpHours = chargedUah,
                avgPowerMw = if (integratedMs > 0) powerMwMs / integratedMs else lastPowerMw,
                peakPowerMw = peakPowerMw,
                peakTemperatureDeciC = peakTempDeciC,
                sampleCount = count,
                completed = completed,
            )
        }

        companion object {
            fun fresh(first: Sample): Builder {
                val p = first.powerMw.coerceAtLeast(0.0)
                return Builder(
                    startMs = first.timestampMs, startLevel = first.levelPercent,
                    lastMs = first.timestampMs, lastLevel = first.levelPercent,
                    lastCurrentUa = first.currentUa.coerceAtLeast(0), lastPowerMw = p,
                    chargedUah = 0.0, powerMwMs = 0.0, integratedMs = 0L,
                    peakPowerMw = p, peakTempDeciC = first.temperatureDeciC, count = 1,
                )
            }

            /** 저장된 스냅숏에서 복원. 적분 시간은 세션 길이로, 전력 적분은 평균 × 길이로 되돌린다. */
            fun from(s: ChargingSession): Builder {
                val integrated = (s.endMs - s.startMs).coerceAtLeast(0)
                return Builder(
                    startMs = s.startMs, startLevel = s.startLevel,
                    lastMs = s.endMs, lastLevel = s.endLevel,
                    lastCurrentUa = 0L, lastPowerMw = 0.0,
                    chargedUah = s.chargedMicroAmpHours, powerMwMs = s.avgPowerMw * integrated, integratedMs = integrated,
                    peakPowerMw = s.peakPowerMw, peakTempDeciC = s.peakTemperatureDeciC, count = s.sampleCount,
                )
            }
        }
    }

    companion object {
        const val DEFAULT_GAP_TOLERANCE_MS = 5 * 60_000L
    }
}
