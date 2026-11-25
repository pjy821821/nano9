package kr.techbridge.battery.service

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kr.techbridge.battery.core.Calibration
import kr.techbridge.battery.core.ChargerMatcher
import kr.techbridge.battery.core.CurrentCalibrator
import kr.techbridge.battery.core.RawSample
import kr.techbridge.battery.core.SessionTracker
import kr.techbridge.battery.data.battery.Remaining
import kr.techbridge.battery.data.battery.RemainingTimes
import kr.techbridge.battery.data.settings.Settings
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.notify.Notifications
import kr.techbridge.battery.widget.BatteryWidget

/**
 * 서비스 안에서 돌아가는 수집기. BatteryReader → Calibration → SessionTracker → Repository/LiveState 순서로 흐른다.
 * 꽂혀 있으면 1초, 아니면 30초 간격으로 샘플링한다.
 */
class SessionRecorder(private val context: Context, private val c: AppContainer) {
    private val tracker = SessionTracker()
    private val mutex = Mutex()
    private val rawBuffer = ArrayDeque<RawSample>()

    private var settings = Settings()
    private var calibration: Calibration = Calibration.SPEC
    private var calibrated = false
    private var sessionId: Long? = null
    private var lastSessionWriteMs = 0L
    private var lastWidgetMs = 0L
    private var limitNotified = false
    private var tempNotified = false
    /** 세션 초반 전력(mW). 협상값으로 못 맞힌 충전기를 전력으로 다시 맞히는 데 쓴다. */
    private val powerWindow = ArrayList<Double>(POWER_MATCH_SAMPLES)
    private var powerMatchDone = false
    /** 마지막으로 뺀 시각. 뺀 직후 [UNPLUGGED_BURST_MS] 동안은 빠르게 샘플링한다. */
    private var unpluggedSinceMs = 0L
    private var lastPlugged: Boolean? = null

    /** 꽂힘·뺌 이벤트로 루프를 깨우는 신호. 합쳐지므로 여러 번 와도 한 번만 깨운다. */
    private val kicks = Channel<Unit>(Channel.CONFLATED)

    // 매초 알림을 갱신할 때 무거운 조회(완충 예상 시간은 binder 호출, 충전기 이름은 DB 두 번)는 캐시한다.
    private var remaining: Remaining? = null
    private var remainingCheckedMs = 0L
    private var remainingPlugged: Boolean? = null
    private var chargerNameCache: String? = null
    private var chargerNameSessionId: Long? = null
    private var chargerNameCheckedMs = 0L

    suspend fun run() = coroutineScope {
        launch {
            c.settings.flow.collect { s ->
                settings = s
                if (s.calibration == null && calibrated) {
                    // 사용자가 재보정을 눌렀다. 다시 추론한다.
                    calibrated = false
                    calibration = Calibration.SPEC
                    rawBuffer.clear()
                } else if (s.calibration != null && !calibrated) {
                    calibration = s.calibration
                    calibrated = true
                }
                c.live.setCalibration(calibration)
            }
        }
        recoverOrphans()
        // 틱 시각을 기준으로 기다린다. "처리 후 1초 쉬기"는 처리 시간(DB 쓰기·알림)만큼 주기가 늘어져 실측 약 1.6초였다.
        var nextDueMs = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            val plugged = sampleNow()
            nextDueMs += if (plugged) PLUGGED_INTERVAL_MS else unpluggedIntervalMs()
            val now = System.currentTimeMillis()
            if (nextDueMs < now) nextDueMs = now // 처리가 간격보다 오래 걸렸으면 밀린 틱을 몰아서 돌리지 않는다
            // 꽂힘·뺌 이벤트가 오면 남은 시간을 기다리지 않고 바로 샘플링한다. 뺀 상태의 30초 간격 중에 꽂히면
            // 예전에는 리시버의 샘플 하나 뒤 최대 30초 동안 세션 샘플이 비었다.
            if (withTimeoutOrNull(nextDueMs - now) { kicks.receive() } != null) nextDueMs = System.currentTimeMillis()
        }
    }

    /**
     * 뺀 상태의 샘플 간격. 뺀 직후 2분은 5초(방전 남은 시간이 약 30초 만에 나오게), 앱 화면을 보고 있으면 10초,
     * 아니면 30초(배터리 절약). 꽂혀 있을 때는 항상 1초.
     */
    private fun unpluggedIntervalMs(): Long {
        val now = System.currentTimeMillis()
        return when {
            now - unpluggedSinceMs < UNPLUGGED_BURST_MS -> UNPLUGGED_BURST_INTERVAL_MS
            c.live.uiWatching -> UNPLUGGED_WATCHING_INTERVAL_MS
            else -> UNPLUGGED_INTERVAL_MS
        }
    }

    /** 꽂힘·뺌 리시버가 부른다. 루프가 대기를 끊고 즉시 샘플링한 뒤 새 간격으로 다시 맞춘다. */
    fun kick() {
        kicks.trySend(Unit)
    }

    /**
     * 서비스가 죽었다 살아난 뒤 처리. 끝맺지 못한 세션 중 가장 최근 것이, 아직 꽂혀 있고 마지막 기록이
     * 간격 허용치 안이면 트래커에 이어받아 세션 하나로 유지한다. 나머지는 마지막 샘플 시각으로 닫는다.
     */
    private suspend fun recoverOrphans() = mutex.withLock {
        val open = c.repository.openSessions()
        if (open.isEmpty()) return@withLock
        val plugged = c.batteryReader.read()?.plugged == true
        val now = System.currentTimeMillis()
        val latest = open.last()
        val latestLastMs = maxOf(latest.endMs, c.repository.lastSampleMs(latest.id) ?: Long.MIN_VALUE)
        val resumable = plugged && now - latestLastMs <= SessionTracker.DEFAULT_GAP_TOLERANCE_MS

        for (entity in open) {
            if (resumable && entity.id == latest.id) {
                val snapshot = entity.toCore().let { if (latestLastMs > it.endMs) it.copy(endMs = latestLastMs) else it }
                tracker.resume(snapshot)
                sessionId = entity.id
                lastSessionWriteMs = now
            } else {
                c.repository.closeInterrupted(entity)
            }
        }
    }

    /** 한 번 샘플링하고 처리한다. 꽂혀 있으면 true. */
    suspend fun sampleNow(): Boolean = mutex.withLock {
        val raw = c.batteryReader.read() ?: return false
        if (lastPlugged == true && !raw.plugged) unpluggedSinceMs = raw.timestampMs
        lastPlugged = raw.plugged
        maybeCalibrate(raw)
        val sample = calibration.normalize(raw)
        val repo = c.repository

        for (event in tracker.onSample(sample)) {
            when (event) {
                is SessionTracker.Event.Started -> {
                    val snapshot = tracker.current ?: continue
                    // 1단계: 연결 종류 + 협상 전류·전압이 같은 충전기가 하나뿐이면 꽂자마자 이름이 붙는다.
                    val fp = raw.fingerprint
                    val matched = ChargerMatcher.matchByNegotiation(fp, repo.knownChargers())
                    sessionId = repo.startSession(snapshot, chargerId = matched, fingerprint = fp)
                    lastSessionWriteMs = sample.timestampMs
                    limitNotified = false
                    tempNotified = false
                    powerWindow.clear()
                    powerMatchDone = matched != null
                }
                is SessionTracker.Event.Progress -> {
                    val id = sessionId ?: continue
                    repo.addSample(id, sample)
                    maybeMatchByPower(id, raw.plugType, sample.powerMw)
                    if (sample.timestampMs - lastSessionWriteMs >= SESSION_WRITE_INTERVAL_MS) {
                        repo.updateSession(id, event.snapshot)
                        lastSessionWriteMs = sample.timestampMs
                    }
                }
                is SessionTracker.Event.Ended -> {
                    sessionId?.let { repo.finishSession(it, event.session) }
                    sessionId = null
                    Notifications.cancel(context, Notifications.ID_LIMIT)
                }
            }
        }

        c.live.publish(sample, tracker.current, sessionId)
        notify(sample)
        alerts(sample)
        if (sample.timestampMs - lastWidgetMs >= WIDGET_INTERVAL_MS) {
            lastWidgetMs = sample.timestampMs
            runCatching { BatteryWidget().updateAll(context) }
        }
        return raw.plugged
    }

    /** 2단계: 초반 [POWER_MATCH_SAMPLES]개 샘플의 전력 중앙값이 어느 한 충전기의 대표 전력과만 맞으면 그 이름을 붙인다. */
    private suspend fun maybeMatchByPower(sessionId: Long, plugType: Int, powerMw: Double) {
        if (powerMatchDone) return
        if (powerMw > 0.0) powerWindow += powerMw
        if (powerWindow.size < POWER_MATCH_SAMPLES) return
        powerMatchDone = true
        val median = ChargerMatcher.median(powerWindow)
        val matched = ChargerMatcher.matchByPower(plugType, median, c.repository.knownChargers()) ?: return
        c.repository.assignChargerIfUnset(sessionId, matched)
    }

    private suspend fun maybeCalibrate(raw: RawSample) {
        if (calibrated) return
        rawBuffer.addLast(raw)
        while (rawBuffer.size > CALIBRATION_WINDOW) rawBuffer.removeFirst()
        val inferred = CurrentCalibrator.infer(rawBuffer.toList()) ?: return
        calibration = inferred
        calibrated = true
        c.settings.setCalibration(inferred)
        c.live.setCalibration(inferred)
    }

    private suspend fun notify(sample: kr.techbridge.battery.core.Sample) {
        if (!settings.ongoingNotification && !sample.plugged) {
            // 상시 알림을 꺼도 포그라운드 서비스에는 알림이 필요하다. 조용한 기본 알림으로 바꾼다.
            Notifications.post(context, Notifications.ID_ONGOING, Notifications.monitoring(context))
            return
        }
        val now = sample.timestampMs
        // 완충까지(꽂힘)·방전까지(뺌) 남은 시간. 꽂힘 상태가 바뀌면 바로, 아니면 30초마다 다시 계산한다.
        if (remainingPlugged != sample.plugged || now - remainingCheckedMs >= REMAINING_REFRESH_MS) {
            remaining = RemainingTimes.compute(c.batteryReader, sample, c.live.recent.value, settings.designCapacityMah)
            remainingCheckedMs = now
            remainingPlugged = sample.plugged
        }
        val chargerName = sessionId?.let { id ->
            if (id != chargerNameSessionId || now - chargerNameCheckedMs >= CHARGER_NAME_REFRESH_MS) {
                chargerNameCache = c.db.sessionDao().get(id)?.chargerId?.let { c.repository.charger(it)?.name }
                chargerNameSessionId = id
                chargerNameCheckedMs = now
            }
            chargerNameCache
        }
        Notifications.post(context, Notifications.ID_ONGOING, Notifications.ongoing(context, sample, tracker.current, remaining, chargerName))
    }

    private fun alerts(sample: kr.techbridge.battery.core.Sample) {
        if (sample.plugged && settings.chargeLimitEnabled && !limitNotified && sample.levelPercent >= settings.chargeLimitPercent) {
            limitNotified = true
            Notifications.post(context, Notifications.ID_LIMIT, Notifications.chargeLimit(context, settings.chargeLimitPercent))
        }
        if (settings.tempWarnEnabled) {
            if (!tempNotified && sample.temperatureDeciC >= settings.tempWarnDeciC) {
                tempNotified = true
                Notifications.post(context, Notifications.ID_TEMP, Notifications.temperature(context, sample.temperatureDeciC))
            } else if (tempNotified && sample.temperatureDeciC < settings.tempWarnDeciC - 30) {
                tempNotified = false // 3도 내려가면 다시 경고 가능
            }
        }
    }

    /** 서비스가 죽을 때 진행 중 세션을 미완료로 저장한다. onDestroy에서 호출하므로 블로킹. */
    fun flushBlocking() {
        val id = sessionId ?: return
        val snapshot = tracker.flush() ?: return
        sessionId = null
        runBlocking { c.repository.finishSession(id, snapshot) }
    }

    companion object {
        const val PLUGGED_INTERVAL_MS = 1_000L
        const val UNPLUGGED_INTERVAL_MS = 30_000L
        const val UNPLUGGED_WATCHING_INTERVAL_MS = 10_000L
        const val UNPLUGGED_BURST_INTERVAL_MS = 5_000L
        const val UNPLUGGED_BURST_MS = 2 * 60_000L
        const val SESSION_WRITE_INTERVAL_MS = 10_000L
        const val WIDGET_INTERVAL_MS = 30_000L
        // 꽂혀 있으면 1초 간격이므로 2분. 충전 카운터 눈금(삼성 약 3.6 mAh)이 약한 USB 충전에서도 움직일 시간이다.
        const val CALIBRATION_WINDOW = 120
        /** 꽂힌 뒤 1분. 초반 전력은 충전기 종류를 가장 잘 드러내고, 완충 근처 트리클보다 앞선다. */
        const val POWER_MATCH_SAMPLES = 60
        const val REMAINING_REFRESH_MS = 30_000L
        const val CHARGER_NAME_REFRESH_MS = 10_000L
    }
}
