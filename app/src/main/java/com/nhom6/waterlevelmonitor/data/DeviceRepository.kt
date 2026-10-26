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

    private val observers =
        LinkedHashSet<(UiState) -> Unit>()

    private var running = false
    private var loading = true
    private var errorMessage: String? = null

    private var lastFirebaseData: DeviceData? = null
    private var device: DeviceData? = null
    private var connected: Boolean? = null

    private var demoActive = false

    // =========================================================
    // TRẠNG THÁI CHỜ LỆNH
    // =========================================================

    private var pendingCommand: Boolean? = null
    private var pendingMode: String? = null
    private var pendingLightCommand: Boolean? = null
    private var pendingAlertCommand: Boolean? = null
    private var pendingPumpCommand: Boolean? = null

    private var pendingSince = 0L

    // =========================================================
    // DEMO
    // =========================================================

    private var currentDemoMode = "AUTO"
    private var currentDemoMotorOn = true
    private var currentDemoLightOn = false
    private var currentDemoAlertOn = false
    private var currentDemoPumpOn = false
    private var currentDemoDistanceCm = 15.0

    private var demoDangBomHaMuc = false

    // =========================================================
    // LỊCH SỬ BIỂU ĐỒ
    // =========================================================

    data class Sample(
        val timeMs: Long,
        val distanceCm: Double,
        val levelCm: Double?
    )

    data class AlertEvent(
        val timeMs: Long,
        val from: AlertLevel,
        val to: AlertLevel
    )

    enum class DeviceEventType {
        MOTOR,
        LIGHT,
        ALERT
    }

    data class DeviceEvent(
        val timeMs: Long,
        val type: DeviceEventType,
        val on: Boolean,
        val automatic: Boolean = false
    )

    private val samples = ArrayDeque<Sample>()
    private val events = ArrayDeque<AlertEvent>()
    private val deviceEvents = ArrayDeque<DeviceEvent>()

    // Dùng cho lịch sử biểu đồ/UI
    private var lastAlert: AlertLevel? = null

    // Dùng RIÊNG cho log Firebase
    private var lastLoggedAlert: AlertLevel? = null
    private var lastAlertLogTime = 0L

    private var recoveryUntil = 0L

    private var lastMotor: Boolean? = null
    private var lastLight: Boolean? = null
    private var lastAlertOutput: Boolean? = null

    private var lastSampleAt = 0L

    // =========================================================
    // FIREBASE DEVICE LISTENER
    // =========================================================

    private val deviceListener =
        object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                val newData =
                    DeviceData.fromSnapshot(snapshot)

                handler.post {

                    loading = false
                    errorMessage = null

                    lastFirebaseData = newData

                    OnlineTracker.onUptime(
                        newData?.uptimeMs
                    )

                    publish()
                }
            }

            override fun onCancelled(
                error: DatabaseError
            ) {

                handler.post {

                    loading = false
                    errorMessage = error.message

                    publish()
                }
            }
        }

    // =========================================================
    // FIREBASE CONNECTION LISTENER
    // =========================================================

    private val connectedListener =
        object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                val isConn =
                    snapshot.getValue(Boolean::class.java)
                        ?: false

                handler.post {

                    connected = isConn

                    publish()
                }
            }

            override fun onCancelled(
                error: DatabaseError
            ) {
            }
        }

    // =========================================================
    // TIMER
    // =========================================================

    private val ticker =
        object : Runnable {

            override fun run() {

                if (!running) {
                    return
                }

                if (demoActive) {
                    updateDemo()
                }

                updateEffectiveDevice()

                // Kiểm tra thay đổi cảnh báo
                checkAlertChange()

                publish()

                handler.postDelayed(
                    this,
                    1000L
                )
            }
        }

    // =========================================================
    // KIỂM TRA THAY ĐỔI CẢNH BÁO
    //
    // CHỈ HÀM NÀY ĐƯỢC GHI LOG LÊN FIREBASE
    // =========================================================

    private fun checkAlertChange() {

        val distanceCm = device?.distanceCm

        // Chưa có dữ liệu khoảng cách
        if (distanceCm == null) {
            return
        }

        // Chiều cao cảm biến/bể
        val sensorHeightCm =
            AppSettings.sensorHeightCm

        // Nếu chưa cài chiều cao thì không tính được mực nước
        if (sensorHeightCm <= 0) {
            return
        }

        // =====================================================
        // TÍNH MỰC NƯỚC
        //
        // sensorHeightCm = chiều cao từ đáy đến cảm biến
        // distanceCm     = khoảng cách từ cảm biến đến mặt nước
        //
        // waterLevelCm = sensorHeightCm - distanceCm
        // =====================================================

        val waterLevelCm =
            (sensorHeightCm - distanceCm)
                .coerceAtLeast(0.0)

        // Xác định trạng thái dựa trên MỰC NƯỚC
        val currentLevel =
            AlertLevel.resolve(
                device,
                waterLevelCm,
                AppSettings.warnLevelCm,
                AppSettings.dangerLevelCm
            )

        if (currentLevel == AlertLevel.UNKNOWN) {
            return
        }

        // RECOVERING chỉ là trạng thái hiển thị,
        // không tạo thêm một log riêng.
        val logLevel =
            when (currentLevel) {
                AlertLevel.RECOVERING -> AlertLevel.MID
                else -> currentLevel
            }

        val now =
            SystemClock.elapsedRealtime()

        // Cùng trạng thái thì không ghi lại
        if (logLevel == lastLoggedAlert) {
            return
        }

        // Chống cảm biến dao động quá nhanh
        if (
            lastAlertLogTime > 0L &&
            now - lastAlertLogTime < 3000L
        ) {
            return
        }

        val entry =
            AlertLogEntry(

                id = "",

                type =
                    when (logLevel) {

                        AlertLevel.DANGER ->
                            "DANGER"

                        AlertLevel.HIGH,
                        AlertLevel.MID ->
                            "WARNING"

                        AlertLevel.SENSOR_ERROR ->
                            "SENSOR_ERROR"

                        AlertLevel.NORMAL,
                        AlertLevel.LOW ->
                            "SAFE"

                        else ->
                            "UNKNOWN"
                    },

                // =================================================
                // QUAN TRỌNG:
                // Firebase sẽ nhận MỰC NƯỚC, không phải khoảng cách
                // =================================================
                waterLevel = waterLevelCm,

                message =
                    logLevel.hint,

                timestamp =
                    System.currentTimeMillis()
            )

        // Chỉ ghi Firebase khi trạng thái thực sự thay đổi
        AlertRepository.addAlert(entry)

        lastLoggedAlert = logLevel
        lastAlertLogTime = now

        Log.d(
            TAG,
            "Cảnh báo: $logLevel | " +
                    "Khoảng cách: ${"%.2f".format(distanceCm)} cm | " +
                    "Mực nước: ${"%.2f".format(waterLevelCm)} cm"
        )
    }
    private fun updateEffectiveDevice() {

        val real =
            if (demoActive) {

                DeviceData(
                    true,
                    currentDemoDistanceCm,
                    currentDemoMode,
                    false,
                    currentDemoMotorOn,
                    currentDemoLightOn,
                    currentDemoAlertOn,
                    currentDemoPumpOn,
                    "OK",
                    SystemClock.elapsedRealtime(),
                    -60
                )

            } else {

                lastFirebaseData
            }

        if (
            pendingMode != null ||
            pendingCommand != null ||
            pendingLightCommand != null ||
            pendingAlertCommand != null ||
            pendingPumpCommand != null
        ) {

            val base =
                real ?: DeviceData(
                    true,
                    null,
                    "AUTO",
                    false,
                    false,
                    false,
                    false,
                    false,
                    "...",
                    null,
                    null
                )

            device =
                base.copy(

                    mode =
                        pendingMode ?: base.mode,

                    motorOn =
                        pendingCommand ?: base.motorOn,

                    lightOn =
                        pendingLightCommand ?: base.lightOn,

                    alertOn =
                        pendingAlertCommand ?: base.alertOn,

                    pumpOn =
                        pendingPumpCommand ?: base.pumpOn
                )

        } else {

            device = real
        }
    }

    // =========================================================
    // START
    // =========================================================

    fun start() {

        if (running) {
            return
        }

        running = true

        demoActive =
            AppSettings.demoMode

        if (!demoActive) {

            FirebaseManager.deviceRef
                .addValueEventListener(
                    deviceListener
                )

            FirebaseManager.connectedRef
                .addValueEventListener(
                    connectedListener
                )
        }

        handler.post(ticker)
    }

    // =========================================================
    // STOP
    // =========================================================

    fun stop() {

        running = false

        handler.removeCallbacks(ticker)

        FirebaseManager.deviceRef
            .removeEventListener(
                deviceListener
            )

        FirebaseManager.connectedRef
            .removeEventListener(
                connectedListener
            )
    }

    // =========================================================
    // OBSERVER
    // =========================================================

    fun addObserver(
        observer: (UiState) -> Unit
    ) {

        observers.add(observer)

        publish()
    }

    fun removeObserver(
        observer: (UiState) -> Unit
    ) {

        observers.remove(observer)
    }

    // =========================================================
    // HISTORY
    // =========================================================

    fun samplesSnapshot(): List<Sample> =
        samples.toList()

    fun eventsSnapshot(): List<AlertEvent> =
        events.toList()

    fun deviceEventsSnapshot(): List<DeviceEvent> =
        deviceEvents.toList()

    fun refresh() {
        publish()
    }

    // =========================================================
    // RESTART
    // =========================================================

    fun restart() {

        val wasRunning =
            running

        stop()

        samples.clear()
        events.clear()
        deviceEvents.clear()

        lastAlert = null

        lastLoggedAlert = null
        lastAlertLogTime = 0L

        recoveryUntil = 0L

        lastMotor = null
        lastLight = null
        lastAlertOutput = null

        lastSampleAt = 0L

        if (wasRunning) {
            start()
        }
    }

    // =========================================================
    // RESET
    // =========================================================

    fun reset() {

        stop()

        samples.clear()
        events.clear()
        deviceEvents.clear()

        lastFirebaseData = null
        device = null
        connected = null

        loading = true

        errorMessage = null

        pendingCommand = null
        pendingMode = null
        pendingLightCommand = null
        pendingAlertCommand = null
        pendingPumpCommand = null

        lastAlert = null

        lastLoggedAlert = null
        lastAlertLogTime = 0L

        recoveryUntil = 0L

        lastMotor = null
        lastLight = null
        lastAlertOutput = null

        lastSampleAt = 0L
    }

    // =========================================================
    // XÓA LỊCH SỬ PHIÊN
    // KHÔNG XÓA FIREBASE
    // =========================================================

    fun clearHistory() {

        samples.clear()
        events.clear()
        deviceEvents.clear()

        lastAlert = null

        // Không reset lastLoggedAlert ở đây.
        // Vì clearHistory chỉ xóa lịch sử UI,
        // không được làm Firebase ghi lại cảnh báo hiện tại.

        lastMotor = null
        lastLight = null
        lastAlertOutput = null

        lastSampleAt = 0L
        recoveryUntil = 0L

        publish()
    }

    // =========================================================
    // ĐIỀU KHIỂN MOTOR
    // =========================================================

    fun sendMotorCommand(
        on: Boolean
    ) {

        pendingCommand = on

        pendingSince =
            SystemClock.elapsedRealtime()

        if (demoActive) {

            currentDemoMotorOn = on

        } else {

            FirebaseManager.deviceRef
                .child("lenhMotor")
                .setValue(on)
        }

        publish()
    }

    // =========================================================
    // ĐỔI CHẾ ĐỘ
    // =========================================================

    fun sendMode(
        mode: String
    ) {

        Log.d(
            TAG,
            "Gửi chế độ: $mode"
        )

        pendingMode = mode

        pendingSince =
            SystemClock.elapsedRealtime()

        if (demoActive) {

            currentDemoMode = mode

            if (mode == "MANUAL") {
                currentDemoMotorOn = false
            }

        } else {

            FirebaseManager.deviceRef
                .child("cheDo")
                .setValue(mode)

            if (mode == "MANUAL") {

                device?.motorOn?.let {
                    FirebaseManager.deviceRef
                        .child("lenhMotor")
                        .setValue(it)
                }

                device?.alertOn?.let {
                    FirebaseManager.deviceRef
                        .child("lenhCoi")
                        .setValue(it)
                }

                device?.pumpOn?.let {
                    FirebaseManager.deviceRef
                        .child("lenhMaybom")
                        .setValue(it)
                }
            }
        }

        publish()
    }

    // =========================================================
    // ĐIỀU KHIỂN ĐÈN
    // =========================================================

    fun sendLightCommand(
        on: Boolean
    ) {

        pendingLightCommand = on

        pendingSince =
            SystemClock.elapsedRealtime()

        if (demoActive) {

            currentDemoLightOn = on

        } else {

            FirebaseManager.deviceRef
                .child("lenhDen")
                .setValue(on)
        }

        publish()
    }

    // =========================================================
    // ĐIỀU KHIỂN CÒI
    // =========================================================

    fun sendAlertCommand(
        on: Boolean
    ) {

        pendingAlertCommand = on

        pendingSince =
            SystemClock.elapsedRealtime()

        if (demoActive) {

            currentDemoAlertOn = on

        } else {

            FirebaseManager.deviceRef
                .child("lenhCoi")
                .setValue(on)
        }

        publish()
    }

    // =========================================================
    // ĐIỀU KHIỂN MÁY BƠM
    // =========================================================

    fun sendPumpCommand(
        on: Boolean
    ) {

        pendingPumpCommand = on

        pendingSince =
            SystemClock.elapsedRealtime()

        if (demoActive) {

            currentDemoPumpOn = on

        } else {

            FirebaseManager.deviceRef
                .child("lenhMaybom")
                .setValue(on)
        }

        publish()
    }

    // =========================================================
    // DEMO
    // =========================================================

    private fun updateDemo() {

        val seconds =
            SystemClock.elapsedRealtime() / 1_000.0

        currentDemoDistanceCm =
            15.0 +
                    7.0 *
                    sin(seconds / 8.0)

        if (currentDemoMode == "AUTO") {

            val h =
                AppSettings.sensorHeightCm
                    .takeIf { it > 0 }
                    ?: 30.0

            val level =
                (
                        h -
                                currentDemoDistanceCm
                        ).coerceAtLeast(0.0)

            val warn =
                AppSettings.warnLevelCm

            val danger =
                AppSettings.dangerLevelCm

            when {

                // NGUY HIỂM
                level >= danger -> {

                    currentDemoMotorOn = false

                    demoDangBomHaMuc = true

                    currentDemoPumpOn = true

                    currentDemoAlertOn = true
                }

                // CẢNH BÁO
                level >= warn -> {

                    currentDemoMotorOn = true

                    currentDemoAlertOn =
                        (
                                SystemClock.elapsedRealtime() /
                                        3000
                                ) % 2 == 0L

                    currentDemoPumpOn =
                        demoDangBomHaMuc
                }

                // AN TOÀN
                else -> {

                    currentDemoMotorOn = true

                    currentDemoPumpOn = false

                    currentDemoAlertOn = false

                    demoDangBomHaMuc = false
                }
            }
        }
    }

    // =========================================================
    // XỬ LÝ LỆNH ĐANG CHỜ
    // =========================================================

    private fun resolvePending() {

        val now =
            SystemClock.elapsedRealtime()

        // Sau 5 giây không nhận được phản hồi
        // thì bỏ trạng thái chờ.

        if (
            pendingSince > 0 &&
            now - pendingSince > 5000L
        ) {

            pendingCommand = null
            pendingMode = null
            pendingLightCommand = null
            pendingAlertCommand = null
            pendingPumpCommand = null

            pendingSince = 0

            return
        }

        val realMode =
            if (demoActive)
                currentDemoMode
            else
                lastFirebaseData?.mode

        val realMotor =
            if (demoActive)
                currentDemoMotorOn
            else
                lastFirebaseData?.motorOn

        val realLight =
            if (demoActive)
                currentDemoLightOn
            else
                lastFirebaseData?.lightOn

        val realAlert =
            if (demoActive)
                currentDemoAlertOn
            else
                lastFirebaseData?.alertOn

        val realPump =
            if (demoActive)
                currentDemoPumpOn
            else
                lastFirebaseData?.pumpOn

        if (
            pendingMode != null &&
            realMode
                ?.trim()
                ?.uppercase(Locale.ROOT)
            ==
            pendingMode
                ?.trim()
                ?.uppercase(Locale.ROOT)
        ) {

            pendingMode = null
        }

        if (
            pendingCommand != null &&
            realMotor == pendingCommand
        ) {

            pendingCommand = null
        }

        if (
            pendingLightCommand != null &&
            realLight == pendingLightCommand
        ) {

            pendingLightCommand = null
        }

        if (
            pendingAlertCommand != null &&
            realAlert == pendingAlertCommand
        ) {

            pendingAlertCommand = null
        }

        if (
            pendingPumpCommand != null &&
            realPump == pendingPumpCommand
        ) {

            pendingPumpCommand = null
        }

        if (
            pendingMode == null &&
            pendingCommand == null &&
            pendingLightCommand == null &&
            pendingAlertCommand == null &&
            pendingPumpCommand == null
        ) {

            pendingSince = 0
        }
    }

    // =========================================================
    // PUBLISH
    // =========================================================

    private fun publish() {

        resolvePending()

        updateEffectiveDevice()

        val state =
            buildState()

        record(state)

        observers.forEach {
            it(state)
        }
    }

    // =========================================================
    // GHI LỊCH SỬ BIỂU ĐỒ
    //
    // LƯU Ý:
    // HÀM NÀY KHÔNG GHI FIREBASE ALERTS
    // =========================================================

    private fun record(
        state: UiState
    ) {

        val d =
            state.device ?: return

        val now =
            SystemClock.elapsedRealtime()

        // =====================================================
        // LƯU SAMPLE
        // =====================================================

        if (
            d.distanceCm != null &&
            now - lastSampleAt >=
            AppSettings.chartIntervalSeconds * 1000L
        ) {

            lastSampleAt = now

            samples.addLast(
                Sample(
                    System.currentTimeMillis(),
                    d.distanceCm,
                    state.levelCm
                )
            )

            HistoryStore.append(
                System.currentTimeMillis(),
                d.distanceCm,
                state.levelCm
            )

            if (samples.size > 1200) {
                samples.removeFirst()
            }

            // =================================================
            // LỊCH SỬ CHUYỂN TRẠNG THÁI TRÊN APP
            // Không ghi Firebase ở đây.
            // =================================================

            val sampledLevel =
                if (
                    state.level ==
                    AlertLevel.RECOVERING
                ) {

                    AlertLevel.MID

                } else {

                    state.level
                }

            if (
                state.level ==
                AlertLevel.RECOVERING
            ) {

                recoveryUntil =
                    now + 3000L
            }

            if (
                sampledLevel !=
                AlertLevel.UNKNOWN
            ) {

                lastAlert
                    ?.takeIf {
                        it != sampledLevel
                    }
                    ?.let {

                        events.addFirst(
                            AlertEvent(
                                System.currentTimeMillis(),
                                it,
                                sampledLevel
                            )
                        )
                    }

                lastAlert =
                    sampledLevel

                while (events.size > 100) {
                    events.removeLast()
                }
            }
        }

        // =====================================================
        // MOTOR EVENT
        // =====================================================

        d.motorOn?.let { on ->

            lastMotor
                ?.takeIf {
                    it != on
                }
                ?.let {

                    addDeviceEvent(
                        DeviceEventType.MOTOR,
                        on,
                        d.mode == "AUTO"
                    )
                }

            lastMotor = on
        }

        // =====================================================
        // LIGHT EVENT
        // =====================================================

        d.lightOn?.let { on ->

            lastLight
                ?.takeIf {
                    it != on
                }
                ?.let {

                    addDeviceEvent(
                        DeviceEventType.LIGHT,
                        on,
                        d.mode == "AUTO"
                    )
                }

            lastLight = on
        }

        // =====================================================
        // ALERT OUTPUT EVENT
        // =====================================================

        d.alertOn?.let { on ->

            lastAlertOutput
                ?.takeIf {
                    it != on
                }
                ?.let {

                    addDeviceEvent(
                        DeviceEventType.ALERT,
                        on,
                        d.mode == "AUTO"
                    )
                }

            lastAlertOutput = on
        }
    }

    // =========================================================
    // THÊM DEVICE EVENT
    // =========================================================

    private fun addDeviceEvent(
        type: DeviceEventType,
        on: Boolean,
        automatic: Boolean = false
    ) {

        deviceEvents.addFirst(
            DeviceEvent(
                System.currentTimeMillis(),
                type,
                on,
                automatic
            )
        )

        while (deviceEvents.size > 100) {
            deviceEvents.removeLast()
        }
    }

    // =========================================================
    // BUILD UI STATE
    // =========================================================

    private fun buildState(): UiState {

        val d =
            device

        val fbConn =
            if (demoActive)
                true
            else
                connected

        val status =
            if (demoActive)
                OnlineTracker.Status.ONLINE
            else
                OnlineTracker.status()

        val online =
            status ==
                    OnlineTracker.Status.ONLINE &&
                    (demoActive || fbConn == true)

        // =====================================================
        // TÍNH MỰC NƯỚC
        //
        // distanceCm = khoảng cách từ cảm biến tới mặt nước
        // levelCm = chiều cao nước
        // =====================================================

        val height =
            AppSettings.sensorHeightCm

        val levelCm =
            if (
                d?.distanceCm != null &&
                height > 0
            ) {

                (
                        height -
                                d.distanceCm
                        ).coerceAtLeast(0.0)

            } else {

                null
            }

        val calculatedLevel =
            AlertLevel.resolve(
                d,
                levelCm,
                AppSettings.warnLevelCm,
                AppSettings.dangerLevelCm
            )

        val displayLevel =
            if (
                calculatedLevel ==
                AlertLevel.MID &&
                (
                        lastAlert ==
                                AlertLevel.HIGH ||
                                SystemClock.elapsedRealtime() <
                                recoveryUntil
                        )
            ) {

                AlertLevel.RECOVERING

            } else {

                calculatedLevel
            }

        return UiState(

            loading = loading,

            errorMessage =
                errorMessage,

            device =
                d,

            firebaseConnected =
                fbConn,

            status =
                status,

            online =
                online,

            lastUpdateMs =
                System.currentTimeMillis(),

            demo =
                demoActive,

            level =
                displayLevel,

            levelCm =
                levelCm,

            sensorHeightCm =
                height,

            warnLevelCm =
                AppSettings.warnLevelCm,

            safeLevelCm =
                AppSettings.dangerLevelCm,

            pendingCommand =
                pendingCommand
        )
    }
}