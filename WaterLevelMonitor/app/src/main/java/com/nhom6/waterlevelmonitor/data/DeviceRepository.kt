package com.nhom6.waterlevelmonitor.data

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale
import kotlin.math.sin

object DeviceRepository {
    private const val TAG = "DeviceRepository"
    private val handler = Handler(Looper.getMainLooper())
    private val observers = LinkedHashSet<(UiState) -> Unit>()

    private var running = false
    private var loading = true
    private var errorMessage: String? = null
    private var lastFirebaseData: DeviceData? = null 
    private var device: DeviceData? = null 
    private var connected: Boolean? = null
    private var demoActive = false

    // Trạng thái chờ
    private var pendingCommand: Boolean? = null
    private var pendingMode: String? = null
    private var pendingLightCommand: Boolean? = null
    private var pendingAlertCommand: Boolean? = null
    private var pendingSince = 0L

    // Demo states
    private var currentDemoMode = "AUTO"
    private var currentDemoMotorOn = false
    private var currentDemoLightOn = false
    private var currentDemoAlertOn = false
    private var currentDemoDistanceCm = 15.0

    // Lịch sử biểu đồ
    data class Sample(val timeMs: Long, val distanceCm: Double, val levelCm: Double?)
    data class AlertEvent(val timeMs: Long, val from: AlertLevel, val to: AlertLevel)
    enum class DeviceEventType { MOTOR, LIGHT, ALERT }
    data class DeviceEvent(val timeMs: Long, val type: DeviceEventType, val on: Boolean, val automatic: Boolean = false)
    private val samples = ArrayDeque<Sample>()
    private val events = ArrayDeque<AlertEvent>()
    private val deviceEvents = ArrayDeque<DeviceEvent>()
    private var lastAlert: AlertLevel? = null
    private var recoveryUntil = 0L
    private var lastMotor: Boolean? = null
    private var lastLight: Boolean? = null
    private var lastAlertOutput: Boolean? = null
    private var lastSampleAt = 0L

    private val deviceListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val newData = DeviceData.fromSnapshot(snapshot)
            handler.post {
                loading = false
                lastFirebaseData = newData
                OnlineTracker.onUptime(newData?.uptimeMs)
                syncAutomaticWarningOutputs(newData)
                publish()
            }
        }
        override fun onCancelled(error: DatabaseError) {
            handler.post { loading = false; errorMessage = error.message; publish() }
        }
    }

    private val connectedListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val isConn = snapshot.getValue(Boolean::class.java) ?: false
            handler.post { connected = isConn; publish() }
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (demoActive) updateDemo()
            publish()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun updateEffectiveDevice() {
        val real = if (demoActive) {
            DeviceData(true, currentDemoDistanceCm, currentDemoMode, false, currentDemoMotorOn, currentDemoLightOn, currentDemoAlertOn, "OK", SystemClock.elapsedRealtime(), -60)
        } else {
            lastFirebaseData
        }

        // QUAN TRỌNG: Nếu có lệnh đang chờ, phải trộn nó vào dữ liệu hiển thị
        if (pendingMode != null || pendingCommand != null || pendingLightCommand != null || pendingAlertCommand != null) {
            // Tạo base data nếu Firebase chưa có dữ liệu để không bị null
            val base = real ?: DeviceData(true, null, "AUTO", false, false, false, false, "...", null, null)
            device = base.copy(
                mode = pendingMode ?: base.mode,
                motorOn = pendingCommand ?: base.motorOn,
                lightOn = pendingLightCommand ?: base.lightOn,
                alertOn = pendingAlertCommand ?: base.alertOn
            )
        } else {
            device = real
        }
    }

    fun start() {
        if (running) return
        running = true
        demoActive = AppSettings.demoMode
        if (!demoActive) {
            FirebaseManager.deviceRef.addValueEventListener(deviceListener)
            FirebaseManager.connectedRef.addValueEventListener(connectedListener)
        }
        handler.post(ticker)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(ticker)
        FirebaseManager.deviceRef.removeEventListener(deviceListener)
        FirebaseManager.connectedRef.removeEventListener(connectedListener)
    }

    fun addObserver(o: (UiState) -> Unit) { observers.add(o); publish() }
    fun removeObserver(o: (UiState) -> Unit) { observers.remove(o) }
    fun samplesSnapshot() = samples.toList()
    fun eventsSnapshot() = events.toList()
    fun deviceEventsSnapshot() = deviceEvents.toList()

    fun refresh() = publish()

    fun restart() {
        val wasRunning = running
        stop()
        samples.clear(); events.clear(); deviceEvents.clear()
        lastAlert = null; recoveryUntil = 0L; lastMotor = null; lastLight = null; lastAlertOutput = null; lastSampleAt = 0L
        if (wasRunning) start()
    }

    fun reset() {
        stop()
        samples.clear(); events.clear(); deviceEvents.clear()
        lastFirebaseData = null; device = null; connected = null; loading = true
        pendingCommand = null; pendingMode = null; pendingLightCommand = null; pendingAlertCommand = null
    }

    /** Xoá lịch sử chỉ lưu trong phiên hiện tại; không động vào dữ liệu Firebase/ESP32. */
    fun clearHistory() {
        samples.clear(); events.clear(); deviceEvents.clear()
        lastAlert = null; lastMotor = null; lastLight = null; lastAlertOutput = null; lastSampleAt = 0L; recoveryUntil = 0L
        publish()
    }

    fun sendMotorCommand(on: Boolean) {
        pendingCommand = on
        pendingSince = SystemClock.elapsedRealtime()
        if (demoActive) { currentDemoMotorOn = on } 
        else { FirebaseManager.deviceRef.child("lenhMotor").setValue(on) }
        publish()
    }

    fun sendMode(mode: String) {
        Log.d(TAG, "Gửi chế độ: $mode")
        pendingMode = mode
        pendingSince = SystemClock.elapsedRealtime()
        if (demoActive) { 
            currentDemoMode = mode
            if (mode == "MANUAL") currentDemoMotorOn = false
        } else {
            FirebaseManager.deviceRef.child("cheDo").setValue(mode)
            if (mode == "MANUAL") FirebaseManager.deviceRef.child("lenhMotor").setValue(false)
        }
        publish()
    }

    /** Gửi lệnh đèn khi người dùng chọn chế độ thủ công. */
    fun sendLightCommand(on: Boolean) {
        pendingLightCommand = on
        pendingSince = SystemClock.elapsedRealtime()
        if (demoActive) currentDemoLightOn = on
        else FirebaseManager.deviceRef.child("lenhDen").setValue(on)
        publish()
    }

    /** Gửi lệnh còi khi người dùng chọn chế độ thủ công. */
    fun sendAlertCommand(on: Boolean) {
        pendingAlertCommand = on
        pendingSince = SystemClock.elapsedRealtime()
        if (demoActive) currentDemoAlertOn = on
        else FirebaseManager.deviceRef.child("lenhCoi").setValue(on)
        publish()
    }

    /**
     * Ở AUTO, đèn và còi luôn đi theo trạng thái thực tế của cổng: mở thì bật,
     * đóng thì tắt. Firmware nhận lệnh qua lenhDen và lenhCoi, còn các trường
     * denCanhBao/canhBao là phản hồi trạng thái thực tế từ thiết bị.
     */
    private fun syncAutomaticWarningOutputs(data: DeviceData?) {
        if (demoActive || data?.mode?.trim()?.uppercase(Locale.ROOT) != "AUTO") return

        val gateOpen = data.motorOn ?: return
        if (data.lightOn != gateOpen && pendingLightCommand != gateOpen) {
            sendLightCommand(gateOpen)
        }
        if (data.alertOn != gateOpen && pendingAlertCommand != gateOpen) {
            sendAlertCommand(gateOpen)
        }
    }

    /** Tạo sóng mực nước giả lập để biểu đồ và các thẻ số liệu luôn có chuyển động. */
    private fun updateDemo() {
        val seconds = SystemClock.elapsedRealtime() / 1_000.0
        currentDemoDistanceCm = 15.0 + 7.0 * sin(seconds / 8.0)
        if (currentDemoMode == "AUTO") {
            currentDemoMotorOn = currentDemoDistanceCm <= AppSettings.warnLevelCm
            currentDemoLightOn = currentDemoMotorOn
            currentDemoAlertOn = currentDemoMotorOn
        }
    }

    private fun resolvePending() {
        val now = SystemClock.elapsedRealtime()
        if (pendingSince > 0 && now - pendingSince > 5000L) {
            pendingCommand = null; pendingMode = null; pendingLightCommand = null; pendingAlertCommand = null; pendingSince = 0
            return
        }

        val realMode = if (demoActive) currentDemoMode else lastFirebaseData?.mode
        val realMotor = if (demoActive) currentDemoMotorOn else lastFirebaseData?.motorOn
        val realLight = if (demoActive) currentDemoLightOn else lastFirebaseData?.lightOn
        val realAlert = if (demoActive) currentDemoAlertOn else lastFirebaseData?.alertOn

        if (pendingMode != null && realMode?.trim()?.uppercase(Locale.ROOT) == pendingMode?.trim()?.uppercase(Locale.ROOT)) {
            pendingMode = null
        }
        if (pendingCommand != null && realMotor == pendingCommand) {
            pendingCommand = null
        }
        if (pendingLightCommand != null && realLight == pendingLightCommand) {
            pendingLightCommand = null
        }
        if (pendingAlertCommand != null && realAlert == pendingAlertCommand) {
            pendingAlertCommand = null
        }
        if (pendingMode == null && pendingCommand == null && pendingLightCommand == null && pendingAlertCommand == null) pendingSince = 0
    }

    private fun publish() {
        resolvePending()
        updateEffectiveDevice()
        val s = buildState()
        record(s)
        observers.forEach { it(s) }
    }

    private fun record(state: UiState) {
        val d = state.device ?: return
        val now = SystemClock.elapsedRealtime()
        if (d.distanceCm != null && now - lastSampleAt >= AppSettings.chartIntervalSeconds * 1_000L) {
            lastSampleAt = now
            samples.addLast(Sample(System.currentTimeMillis(), d.distanceCm, state.levelCm))
            if (samples.size > 1200) samples.removeFirst()
            // RECOVERING chỉ xuất hiện trong đúng một chu kỳ đo 3 giây.
            // Sau khi đã xác nhận mẫu tiếp theo, vùng giữa ngưỡng quay lại MID (Nước dâng).
            val sampledLevel = if (state.level == AlertLevel.RECOVERING) AlertLevel.MID else state.level
            if (state.level == AlertLevel.RECOVERING) recoveryUntil = now + 3_000L
            if (sampledLevel != AlertLevel.UNKNOWN) {
                lastAlert?.takeIf { it != sampledLevel }?.let {
                    events.addFirst(AlertEvent(System.currentTimeMillis(), it, sampledLevel))
                }
                lastAlert = sampledLevel
                while (events.size > 100) events.removeLast()
            }
        }
        d.motorOn?.let { on ->
            lastMotor?.takeIf { it != on }?.let { addDeviceEvent(DeviceEventType.MOTOR, on, d.mode == "AUTO") }
            lastMotor = on
        }
        d.lightOn?.let { on -> lastLight?.takeIf { it != on }?.let { addDeviceEvent(DeviceEventType.LIGHT, on, d.mode == "AUTO") }; lastLight = on }
        d.alertOn?.let { on -> lastAlertOutput?.takeIf { it != on }?.let { addDeviceEvent(DeviceEventType.ALERT, on, d.mode == "AUTO") }; lastAlertOutput = on }
    }

    private fun addDeviceEvent(type: DeviceEventType, on: Boolean, automatic: Boolean = false) {
        deviceEvents.addFirst(DeviceEvent(System.currentTimeMillis(), type, on, automatic))
        while (deviceEvents.size > 100) deviceEvents.removeLast()
    }

    private fun buildState(): UiState {
        val d = device
        val fbConn = if (demoActive) true else connected
        val status = if (demoActive) OnlineTracker.Status.ONLINE else OnlineTracker.status()
        val online = status == OnlineTracker.Status.ONLINE && (demoActive || fbConn == true)
        val height = AppSettings.sensorHeightCm
        val levelCm = if (d?.distanceCm != null && height > 0) (height - d.distanceCm).coerceAtLeast(0.0) else null
        
        val calculatedLevel = AlertLevel.resolve(d, d?.distanceCm, AppSettings.warnLevelCm, AppSettings.safeLevelCm)
        // HIGH → MID là lúc nước vừa hạ khỏi ngưỡng lũ. Hiển thị hồi phục trong
        // một mẫu; record() sẽ đổi mốc sang MID ở mẫu 3 giây kế tiếp.
        val displayLevel = if (calculatedLevel == AlertLevel.MID &&
            (lastAlert == AlertLevel.HIGH || SystemClock.elapsedRealtime() < recoveryUntil)) {
            AlertLevel.RECOVERING
        } else calculatedLevel

        return UiState(
            loading = loading, errorMessage = errorMessage, device = d, firebaseConnected = fbConn,
            status = status, online = online, lastUpdateMs = System.currentTimeMillis(),
            demo = demoActive, level = displayLevel,
            levelCm = levelCm, sensorHeightCm = height, warnLevelCm = AppSettings.warnLevelCm, safeLevelCm = AppSettings.safeLevelCm,
            pendingCommand = pendingCommand
        )
    }
}
