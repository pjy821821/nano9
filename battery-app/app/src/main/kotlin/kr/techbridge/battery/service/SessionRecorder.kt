package kr.techbridge.battery.service

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.techbridge.battery.core.Calibration
import kr.techbridge.battery.core.CurrentCalibrator
import kr.techbridge.battery.core.RawSample
import kr.techbridge.battery.core.SessionTracker
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
        while (currentCoroutineContext().isActive) {
            val plugged = sampleNow()
            delay(if (plugged) PLUGGED_INTERVAL_MS else UNPLUGGED_INTERVAL_MS)
        }
    }

    /** 한 번 샘플링하고 처리한다. 꽂혀 있으면 true. */
    suspend fun sampleNow(): Boolean = mutex.withLock {
        val raw = c.batteryReader.read() ?: return false
        maybeCalibrate(raw)
        val sample = calibration.normalize(raw)
        val repo = c.repository

        for (event in tracker.onSample(sample)) {
            when (event) {
                is SessionTracker.Event.Started -> {
                    val snapshot = tracker.current ?: continue
                    sessionId = repo.startSession(snapshot, chargerId = settings.lastChargerId)
                    lastSessionWriteMs = sample.timestampMs
                    limitNotified = false
                    tempNotified = false
                }
                is SessionTracker.Event.Progress -> {
                    val id = sessionId ?: continue
                    repo.addSample(id, sample)
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
        val remaining = if (sample.plugged) c.batteryReader.deviceInfo().chargeTimeRemainingMs else null
        val chargerName = sessionId?.let { id ->
            c.db.sessionDao().get(id)?.chargerId?.let { c.repository.charger(it)?.name }
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
        const val SESSION_WRITE_INTERVAL_MS = 10_000L
        const val WIDGET_INTERVAL_MS = 30_000L
        // 꽂혀 있으면 1초 간격이므로 2분. 충전 카운터 눈금(삼성 약 3.6 mAh)이 약한 USB 충전에서도 움직일 시간이다.
        const val CALIBRATION_WINDOW = 120
    }
}
