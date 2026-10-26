package com.nhom6.waterlevelmonitor.data

object AppConfig {
    /**
     * Khoảng cách (cm) từ cảm biến HC-SR04 đến đáy bể khi bể RỖNG.
     * Để 0.0 nếu chưa đo: app sẽ hiện "--" thay vì tính phần trăm sai.
     * Giai đoạn 4 sẽ cho nhập ngay trong màn Cài đặt.
     */
    const val TANK_HEIGHT_CM = 0.0
}