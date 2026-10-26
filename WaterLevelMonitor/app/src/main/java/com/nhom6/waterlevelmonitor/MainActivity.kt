package com.nhom6.waterlevelmonitor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nhom6.waterlevelmonitor.data.AppSettings
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.FirebaseManager
import com.nhom6.waterlevelmonitor.ui.OverviewFragment
import com.nhom6.waterlevelmonitor.ui.AlertsFragment
import com.nhom6.waterlevelmonitor.ui.PlaceholderFragment
import com.nhom6.waterlevelmonitor.ui.SettingsFragment
import com.nhom6.waterlevelmonitor.ui.StatsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.init(this)
        AppCompatDelegate.setDefaultNightMode(
            if (AppSettings.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)

        // Chưa đăng nhập thì quay về màn hình đăng nhập
        if (FirebaseManager.auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            showScreen(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_overview
        }
    }

    // Nhận dữ liệu khi app hiện ra, dừng khi app ẩn đi
    override fun onStart() {
        super.onStart()
        DeviceRepository.start()
    }

    override fun onStop() {
        super.onStop()
        DeviceRepository.stop()
    }

    private fun showScreen(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_overview -> OverviewFragment()
            R.id.nav_settings -> SettingsFragment()
            R.id.nav_stats -> StatsFragment()
            R.id.nav_alerts -> AlertsFragment()
            else -> return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
