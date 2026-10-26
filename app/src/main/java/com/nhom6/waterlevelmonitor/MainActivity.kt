package com.nhom6.waterlevelmonitor

import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nhom6.waterlevelmonitor.data.AppSettings
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.AlertLevel
import com.nhom6.waterlevelmonitor.data.FirebaseManager
import com.nhom6.waterlevelmonitor.data.DailyCsvExportScheduler
import com.nhom6.waterlevelmonitor.ui.OverviewFragment
import com.nhom6.waterlevelmonitor.ui.AlertsFragment
import com.nhom6.waterlevelmonitor.ui.PlaceholderFragment
import com.nhom6.waterlevelmonitor.ui.SettingsFragment
import com.nhom6.waterlevelmonitor.ui.StatsFragment

class MainActivity : AppCompatActivity() {
    private var previousAlert: AlertLevel? = null
    private var dangerShownForCurrentIncident = false
    private var dangerDialog: AlertDialog? = null
    private lateinit var warningNotification: TextView
    private var warningDismiss: Runnable? = null
    private var flashAnimator: ValueAnimator? = null
    private val alertObserver: (com.nhom6.waterlevelmonitor.data.UiState) -> Unit = { state ->
        val level = state.level
        when (level) {
            AlertLevel.DANGER -> {
                hideWarningNotification(immediate = true)
                if (!dangerShownForCurrentIncident) {
                    dangerShownForCurrentIncident = true
                    showDangerAlert()
                }
            }
            AlertLevel.HIGH -> {
                dismissDangerAlert()
                dangerShownForCurrentIncident = false
                if (previousAlert != AlertLevel.HIGH) showWarningNotification()
            }
            else -> {
                dismissDangerAlert()
                dangerShownForCurrentIncident = false
                hideWarningNotification(immediate = true)
            }
        }
        previousAlert = level
    }

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
        DailyCsvExportScheduler.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        warningNotification = findViewById(R.id.warningNotification)

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
        DeviceRepository.addObserver(alertObserver)
        DeviceRepository.start()
    }

    override fun onStop() {
        DeviceRepository.removeObserver(alertObserver)
        dismissDangerAlert()
        hideWarningNotification(immediate = true)
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

    private fun showDangerAlert() {
        if (isFinishing || dangerDialog?.isShowing == true) return
        startFlashing()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(42, 32, 42, 24) }
        content.addView(ImageView(this).apply { setImageResource(R.drawable.ic_danger_triangle); contentDescription = "Biểu tượng nguy hiểm"; layoutParams = LinearLayout.LayoutParams(220, 200) })
        content.addView(TextView(this).apply { text = "NGUY HIỂM"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.RED); setTypeface(typeface, 1) })
        content.addView(TextView(this).apply { text = "Mực nước đã đạt ngưỡng nguy hiểm. Hãy xử lý ngay."; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 0) })
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(content).setCancelable(false).setPositiveButton("XÁC NHẬN") { dialog, _ -> stopFlashing(); dialog.dismiss() }.show()
        dangerDialog = dialog
        dialog.setOnDismissListener {
            if (dangerDialog === dialog) dangerDialog = null
        }
    }

    /** Đóng cảnh báo nguy hiểm ngay khi mực nước hạ xuống cảnh báo hoặc an toàn. */
    private fun dismissDangerAlert() {
        dangerDialog?.dismiss()
        dangerDialog = null
        stopFlashing()
    }

    /** Banner xuất hiện từ cạnh trên, giống thông báo nổi của điện thoại. */
    private fun showWarningNotification() {
        warningDismiss?.let(warningNotification::removeCallbacks)
        warningNotification.animate().cancel()
        warningNotification.visibility = View.VISIBLE
        warningNotification.alpha = 0f
        warningNotification.translationY = -120f * resources.displayMetrics.density
        warningNotification.animate().translationY(0f).alpha(1f).setDuration(280).start()
        val dismiss = Runnable { hideWarningNotification() }
        warningDismiss = dismiss
        warningNotification.postDelayed(dismiss, 4_500L)
    }

    private fun hideWarningNotification(immediate: Boolean = false) {
        if (!::warningNotification.isInitialized) return
        warningDismiss?.let(warningNotification::removeCallbacks)
        warningDismiss = null
        warningNotification.animate().cancel()
        if (immediate || warningNotification.visibility != View.VISIBLE) {
            warningNotification.visibility = View.GONE
            return
        }
        warningNotification.animate().translationY(-120f * resources.displayMetrics.density)
            .alpha(0f).setDuration(180).withEndAction {
                warningNotification.visibility = View.GONE
            }.start()
    }

    private fun startFlashing() {
        if (flashAnimator != null) return
        flashAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply { duration = 650; repeatCount = ValueAnimator.INFINITE; addUpdateListener {
            findViewById<android.view.View>(R.id.main).setBackgroundColor(Color.rgb(255, (255 * (1f - (it.animatedValue as Float))).toInt(), (255 * (1f - (it.animatedValue as Float))).toInt()))
        }; start() }
    }
    private fun stopFlashing() { flashAnimator?.cancel(); flashAnimator = null; findViewById<android.view.View>(R.id.main)?.setBackgroundResource(R.color.bg) }
}
