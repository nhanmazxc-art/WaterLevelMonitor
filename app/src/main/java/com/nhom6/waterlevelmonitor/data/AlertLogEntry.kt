package com.nhom6.waterlevelmonitor.data

/**
 * Data class representing a single alert log entry stored under Firebase node "/alerts".
 *
 * JSON structure:
 * {
 *   "id": "alert_id",
 *   "type": "DANGER" | "WARNING" | "SENSOR_ERROR" | "SAFE",
 *   "waterLevel": 15.2,
 *   "message": "Mực nước vượt ngưỡng nguy hiểm",
 *   "timestamp": 1729833600000
 * }
 */

data class AlertLogEntry(
    val id: String = "",
    val type: String = "",
    val waterLevel: Double = 0.0,
    val message: String = "",
    val timestamp: Long = 0L
)
