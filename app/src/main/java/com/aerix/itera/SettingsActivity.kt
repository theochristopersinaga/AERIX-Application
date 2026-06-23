package com.aerix.itera

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aerix.itera.databinding.ActivitySettingsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.charts.LineChart
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var chartMarkerCO: ChartMarkerCO
    private lateinit var chartMarkerPM: ChartMarkerPM
    private lateinit var historyListener: ValueEventListener
    private lateinit var db: DatabaseReference
    private val historyList = ArrayList<SensorHistory>()
    private lateinit var adapter: HistoryAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseDatabase.getInstance(
            "https://aerix-2425-01-104-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).reference.child("HISTORY")

        binding.btnBack.setOnClickListener { finish() }

        // =========================
        // RecyclerView
        // =========================
        layoutManager = LinearLayoutManager(this)
        binding.rvHistory.layoutManager = layoutManager

        adapter = HistoryAdapter(historyList)
        binding.rvHistory.adapter = adapter

        // =========================
        // Marker Chart
        // =========================
        chartMarkerCO = ChartMarkerCO(this, R.layout.marker_view)
        chartMarkerPM = ChartMarkerPM(this, R.layout.marker_view)

        binding.chartCOSettings.marker = chartMarkerCO
        binding.chartPMSettings.marker = chartMarkerPM

        // =========================
        // ENABLE INTERACTION CHART
        // =========================
        setupChartInteraction(binding.chartCOSettings)
        setupChartInteraction(binding.chartPMSettings)

        listenHistory()
    }

    private fun setupChartInteraction(chart: LineChart) {
        chart.apply {
            setTouchEnabled(true)
            isDragEnabled = true

            // Zoom
            setScaleEnabled(true)
            setPinchZoom(true)
            setScaleXEnabled(true)
            setScaleYEnabled(true)

            // Smooth drag
            dragDecelerationFrictionCoef = 0.9f

            // Highlight
            isHighlightPerTapEnabled = true
            isHighlightPerDragEnabled = true

            // Double tap zoom
            isDoubleTapToZoomEnabled = true

            // Biar bisa scroll
            setVisibleXRangeMinimum(10f)
            setVisibleXRangeMaximum(30f)

            description.isEnabled = false
        }
    }

    private fun listenHistory() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        historyListener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                // =========================
                // CEK POSISI USER
                // =========================
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val isAtBottom = lastVisible >= historyList.size - 2

                // =========================
                // UPDATE DATA
                // =========================
                historyList.clear()

                for (child in snapshot.children) {
                    historyList.add(
                        SensorHistory(
                            timeStr = child.key ?: "--:--:--",
                            CO_PPM = child.child("CO_PPM").getValue(Float::class.java) ?: 0f,
                            PM25 = child.child("PM25").getValue(Float::class.java) ?: 0f,
                            VIN_INA = child.child("VIN_V").getValue(Float::class.java) ?: 0f,
                            ARUS_INA = child.child("CURRENT_mA").getValue(Float::class.java) ?: 0f,
                            POWER = child.child("POWER").getValue(Float::class.java) ?: 0f
                        )
                    )
                }

                adapter.notifyDataSetChanged()

                // =========================
                // AUTO SCROLL SMART
                // =========================
                if (isAtBottom && historyList.isNotEmpty()) {
                    binding.rvHistory.scrollToPosition(historyList.size - 1)
                }

                updateChart()

                binding.txtLastSync.text =
                    "Last Sync: ${
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    }"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SettingsActivity", error.message)
            }
        }

        db.child(today)
            .limitToLast(100)
            .addValueEventListener(historyListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::historyListener.isInitialized) {
            db.removeEventListener(historyListener)
        }
    }

    private fun updateChart() {
        if (historyList.isEmpty()) {
            binding.chartCOSettings.clear()
            binding.chartPMSettings.clear()
            return
        }

        // ======================
        // CHART CO
        // ======================
        val coEntries = historyList.mapIndexed { i, d ->
            Entry(i.toFloat(), d.CO_PPM)
        }

        val coDataSet = LineDataSet(coEntries, "CO (ppm)").apply {
            lineWidth = 2f
            color = Color.WHITE
            setDrawValues(false)
            setDrawCircles(true)
            setCircleColor(Color.WHITE)
            circleRadius = 3f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.chartCOSettings.apply {
            clear()
            data = LineData(coDataSet)
            notifyDataSetChanged()
            invalidate()

            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                granularity = 1f
            }

            legend.textColor = Color.WHITE

            // Scroll ke data terbaru (chart)
            moveViewToX(historyList.size.toFloat())
        }

        // ======================
        // CHART PM2.5
        // ======================
        val pmEntries = historyList.mapIndexed { i, d ->
            Entry(i.toFloat(), d.PM25)
        }

        val pmDataSet = LineDataSet(pmEntries, "PM2.5 (µg/m³)").apply {
            lineWidth = 2f
            color = Color.WHITE
            setDrawValues(false)
            setDrawCircles(true)
            setCircleColor(Color.WHITE)
            circleRadius = 3f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.chartPMSettings.apply {
            clear()
            data = LineData(pmDataSet)
            notifyDataSetChanged()
            invalidate()

            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                granularity = 1f
            }

            legend.textColor = Color.WHITE

            // Scroll ke data terbaru (chart)
            moveViewToX(historyList.size.toFloat())
        }
    }
}

//
// =========================
// MARKER CO
// =========================
class ChartMarkerCO(
    context: android.content.Context,
    layout: Int
) : com.github.mikephil.charting.components.MarkerView(context, layout) {

    private val tvContent: android.widget.TextView = findViewById(R.id.tvMarker)

    override fun refreshContent(
        e: Entry?,
        highlight: com.github.mikephil.charting.highlight.Highlight?
    ) {
        e?.let {
            tvContent.text = "CO: ${it.y} ppm"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

//
// =========================
// MARKER PM2.5
// =========================
class ChartMarkerPM(
    context: android.content.Context,
    layout: Int
) : com.github.mikephil.charting.components.MarkerView(context, layout) {

    private val tvContent: android.widget.TextView = findViewById(R.id.tvMarker)

    override fun refreshContent(
        e: Entry?,
        highlight: com.github.mikephil.charting.highlight.Highlight?
    ) {
        e?.let {
            tvContent.text = "PM2.5: ${it.y} µg/m³"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}