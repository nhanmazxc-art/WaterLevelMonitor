package com.nhom6.waterlevelmonitor.ui

import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.UiState
import kotlin.math.max

class StatsFragment : Fragment(R.layout.fragment_stats) {
    private lateinit var content: View
    private lateinit var waterPanel: View
    private lateinit var motorPanel: View
    private lateinit var tabs: MaterialButtonToggleGroup
    private lateinit var ranges: MaterialButtonToggleGroup
    private lateinit var barChart: BarChartView
    private lateinit var current: TextView
    private lateinit var highest: TextView
    private lateinit var lowest: TextView
    private lateinit var average: TextView
    private lateinit var warnings: TextView
    private lateinit var rate: TextView
    private lateinit var motorTotal: TextView
    private lateinit var motorOpen: TextView
    private lateinit var motorClose: TextView
    private lateinit var motorHistory: LinearLayout
    private var rangeMs = 3_600_000L
    private var snapshotEndMs = 0L
    private var frozenSamples: List<DeviceRepository.Sample>? = null
    private var lastState: UiState? = null
    private val observer: (UiState) -> Unit = { render(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        content=view; waterPanel=view.findViewById(R.id.waterStatsPanel); motorPanel=view.findViewById(R.id.motorStatsPanel)
        tabs=view.findViewById(R.id.statsTabs); ranges=view.findViewById(R.id.statsRanges); barChart=view.findViewById(R.id.barChart)
        current=view.findViewById(R.id.statCurrent); highest=view.findViewById(R.id.statHighest); lowest=view.findViewById(R.id.statLowest); average=view.findViewById(R.id.statAverage); warnings=view.findViewById(R.id.statWarnings); rate=view.findViewById(R.id.statRate)
        motorTotal=view.findViewById(R.id.motorTotal); motorOpen=view.findViewById(R.id.motorOpen); motorClose=view.findViewById(R.id.motorClose); motorHistory=view.findViewById(R.id.motorHistory)
        tabs.addOnButtonCheckedListener { _,id,checked -> if(checked) { TransitionManager.beginDelayedTransition(content as android.view.ViewGroup); waterPanel.visibility=if(id==R.id.tabWater) View.VISIBLE else View.GONE; motorPanel.visibility=if(id==R.id.tabMotor) View.VISIBLE else View.GONE } }
        ranges.addOnButtonCheckedListener { _,id,checked -> if(checked) { rangeMs=when(id){R.id.rangeWeek->7L*86_400_000L;R.id.rangeMonth->30L*86_400_000L;else->3_600_000L}; snapshotEndMs=System.currentTimeMillis(); frozenSamples=null; lastState?.let(::render) } }
        tabs.check(R.id.tabWater); ranges.check(R.id.rangeHour)
    }
    override fun onStart(){super.onStart();DeviceRepository.addObserver(observer)}
    override fun onStop(){super.onStop();DeviceRepository.removeObserver(observer)}
    private fun render(state: UiState){
        lastState=state
        if (snapshotEndMs == 0L) snapshotEndMs = System.currentTimeMillis()
        val now=snapshotEndMs
        val raw = frozenSamples ?: DeviceRepository.samplesSnapshot().also { frozenSamples = it }
        val samples=raw.filter{it.timeMs in (now-rangeMs)..now}.mapNotNull{it.levelCm?.let { v->ChartView.Point(it.timeMs,v)}}
        val values=samples.map{it.value}; val avg=if(values.isEmpty())null else values.average(); val hi=values.maxOrNull();val lo=values.minOrNull()
        current.text=state.levelCm?.let(::format)?:"--"; highest.text=hi?.let(::format)?:"--";lowest.text=lo?.let(::format)?:"--";average.text=avg?.let(::format)?:"--"
        val warningTransitions=DeviceRepository.eventsSnapshot().count{it.timeMs>=now-rangeMs && it.to.name=="HIGH"};warnings.text="$warningTransitions lần"
        rate.text=if(samples.size>=2){ val first=samples.first();val last=samples.last(); val minutes=max(1.0,(last.timeMs-first.timeMs)/60_000.0); String.format(VI,"%+.1f cm/phút",(last.value-first.value)/minutes)}else"--"
        val bucketCount=if(rangeMs == 7L*86_400_000L) 7 else 6; val start=now-rangeMs; val buckets=(0 until bucketCount).map { i -> val a=start+rangeMs*i/bucketCount; val b=start+rangeMs*(i+1)/bucketCount; val group=samples.filter{it.timeMs>=a && it.timeMs < b}; BarChartView.Bar(bucketLabel(i, bucketCount),group.map{it.value}.average().takeUnless{it.isNaN()}) }
        barChart.setData(buckets,state.warnLevelCm)
        val deviceEvents=DeviceRepository.deviceEventsSnapshot().filter{it.timeMs>=now-rangeMs};motorTotal.text="${deviceEvents.size}";motorOpen.text="${deviceEvents.count{it.on}}";motorClose.text="${deviceEvents.count{!it.on}}"
        motorHistory.removeAllViews(); if(deviceEvents.isEmpty()) addHistory("Chưa có thao tác thiết bị trong khoảng này", "Các lần bật/tắt motor, đèn và cảnh báo sẽ hiện ở đây.", false) else deviceEvents.take(8).forEach { e ->
            val device=when(e.type){ DeviceRepository.DeviceEventType.MOTOR->"Motor"; DeviceRepository.DeviceEventType.LIGHT->"Đèn"; DeviceRepository.DeviceEventType.ALERT->"Cảnh báo" }
            val source=if(e.type==DeviceRepository.DeviceEventType.MOTOR) if(e.automatic)"Tự động" else "Thủ công" else "Thiết bị"
            addHistory("$device đã ${if(e.on)"bật" else "tắt"}", "$source · ${formatTime(e.timeMs)}",e.on)
        }
    }
    private fun bucketLabel(index: Int, count: Int): String {
        if (index == count - 1) return "Hiện tại"
        val remaining = count - 1 - index
        return when (rangeMs) {
            3_600_000L -> "−${remaining * 10}p"
            7L * 86_400_000L -> "−${remaining}d"
            else -> "−${remaining * 5}d"
        }
    }
    private fun addHistory(title:String, sub:String, on:Boolean){ val v=layoutInflater.inflate(R.layout.item_motor_history,motorHistory,false);v.findViewById<TextView>(R.id.historyIcon).apply{text=if(on)"↗" else "↘";setTextColor(ContextCompat.getColor(requireContext(),if(on)R.color.green else R.color.primary))};v.findViewById<TextView>(R.id.historyTitle).text=title;v.findViewById<TextView>(R.id.historySub).text=sub;motorHistory.addView(v) }
    private fun format(v:Double)=String.format(VI,"%.1f cm",v)
}
