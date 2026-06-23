package com.aerix.itera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.view.View

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope

import com.aerix.itera.databinding.ActivityMainBinding
import com.google.firebase.database.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CHANNEL_ID = "CO_ALERT"
        private const val NOTIF_ID = 300
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: DatabaseReference

    private var lastUiUpdate: Long = 0L
    private var deviceOnline = true
    private var maxCOSession = 0.0

    private var maxPMsession = 0.0

    // ===== ALARM CONTROL =====
    private val alarmHandler = Handler(Looper.getMainLooper())
    private var alarmRunning = false

    private val alarmRunnable = object : Runnable {
        override fun run() {
            showCOHazardNotification(lastCOValue)
            alarmHandler.postDelayed(this, 12000) // ulang tiap 6 detik
        }
    }

    private var lastCOValue = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()

        db = FirebaseDatabase.getInstance(
            "https://aerix-2425-01-104-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).reference

        setupUI()
        listenSensor()
        listenINA()
        listenStatus()
        listenControl()
    }

    /* ================= NOTIFICATION ================= */

    private fun createNotificationChannel() {

        val soundUri: Uri =
            "android.resource://${packageName}/${R.raw.co_alarm}".toUri()

        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "CO Hazard Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Peringatan kadar CO berbahaya"
            enableVibration(true)
            vibrationPattern = longArrayOf(
                0,
                1000, 500,
                1000, 500,
                1000, 500,
                1000
            )
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
            lightColor = Color.RED
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun showCOHazardNotification(co: Double) {

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aerix_logo)
            .setContentTitle("🚨 BAHAYA GAS CO")
            .setContentText("Kadar CO %.1f ppm – Segera nyalakan ESP!".format(co))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(this)
            .notify(NOTIF_ID, notification)
    }

    private fun startCOAlarm(co: Double) {
        lastCOValue = co
        if (!alarmRunning) {
            alarmRunning = true
            alarmRunnable.run()
        }
    }

    private fun stopCOAlarm() {
        alarmRunning = false
        alarmHandler.removeCallbacks(alarmRunnable)
        NotificationManagerCompat.from(this).cancel(NOTIF_ID)
    }

    /* ================= UI ================= */

    private fun setupUI() {
        // === About Us ===
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, AboutUsActivity::class.java))
        }

        // === History ===
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.switchPower.setOnCheckedChangeListener { _, isChecked ->
            db.child("CONTROL").child("ZVS_ALLOW").setValue(isChecked)
        }
    }

    /* ================= SENSOR ================= */

    private fun listenSensor() {
        db.child("SENSOR").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val co = snapshot.child("CO").getValue(Double::class.java) ?: 0.0
                val pm = snapshot.child("PM25").getValue(Double::class.java) ?: 0.0

                binding.tvCO.text = "%.1f ppm".format(co)
                binding.tvPM.text = "PM 2.5: %.1f µg/m³".format(pm)


                if (co > maxCOSession) maxCOSession = co
                if (pm > maxPMsession) maxPMsession = pm

                updateCOProgress(co)
                updatePMProgress(pm)
                pushToHistory(co, pm)
                updateLastSync()


                // ===== AUTO ALARM CO =====
                if (co > 500) {
                    startCOAlarm(co)
                } else {
                    stopCOAlarm()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /* ================= INA219 ================= */

    private fun listenINA() {
        db.child("INA219").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val vin =
                    snapshot.child("VIN").getValue(Double::class.java) ?: 0.0
                val currentA =
                    snapshot.child("CURRENT").getValue(Double::class.java) ?: 0.0
                val power =
                    snapshot.child("POWER").getValue(Double::class.java) ?: 0.0

                binding.tvInputVoltage.text = "V: %.2f V".format(vin)
                binding.tvInputCurrent.text = "I: %.2f mA".format(currentA * 1000)
                binding.tvPower.text = "P: %.2f W".format(power)

                updateLastSync()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /* ================= STATUS ================= */

    private fun listenStatus() {
        db.child("STATUS").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val zvsActive =
                    snapshot.child("ZVS_ACTIVE").getValue(Boolean::class.java) ?: false
                val lock =
                    snapshot.child("LOCK").getValue(Boolean::class.java) ?: false

                updateZVSStatusUI(zvsActive)

                binding.alertIndicator.visibility =
                    if (lock) View.VISIBLE else View.GONE

                binding.switchPower.isEnabled = !lock
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /* ================= CONTROL ================= */

    private fun listenControl() {
        db.child("CONTROL").child("ZVS_ALLOW")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val allow = snapshot.getValue(Boolean::class.java) ?: false
                    if (binding.switchPower.isChecked != allow) {
                        binding.switchPower.isChecked = allow
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ================= META ================= */

    private fun listenMeta() {
        db.child("META").child("DEVICE_ONLINE")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    deviceOnline = snapshot.getValue(Boolean::class.java) ?: true
                    updateLastSync()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ================= UI HELPERS ================= */

    private fun updateZVSStatusUI(active: Boolean) {
        if (active) {
            binding.tvZVSStatus.text = "Active"
            binding.tvZVSStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.tvZVSStatus.text = "Inactive"
            binding.tvZVSStatus.setTextColor(Color.parseColor("#FF4444"))
        }
    }
    private fun updateLastSync() {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < 500) return
        lastUiUpdate = now

        val timeStr = SimpleDateFormat("HH:mm:ss:SS", Locale.getDefault())
            .format(Date(now))

        val offlineText = if (!deviceOnline) "\nDevice Offline" else ""

        binding.txtInfo.text =
            "Max CO (Session): %.1f ppm\nMax PM (Session): %.1f µg/m³\nLast Sync: %s%s"
                .format(maxCOSession, maxPMsession, timeStr, offlineText)
    }

    private fun updatePMProgress(pm: Double) {
        val maxPM = 1000.0
        binding.progressPM.progress = ((pm / maxPM).coerceIn(0.0, 1.0) * 1000).toInt()

        when {
            pm <= 200 -> setPMStatus("LOW", "#2ECC71")
            pm <= 350 -> setPMStatus("MID", "#F1C40F")
            else -> setPMStatus("HIGH", "#E74C3C")
        }
    }

    private fun setPMStatus(text: String, colorHex: String) {
        val color = Color.parseColor(colorHex)
        binding.progressPM.progressDrawable.setTint(color)
        binding.keteranganPM.text = text
        binding.keteranganPM.setTextColor(color)
    }

    private fun updateCOProgress(co: Double) {
        val maxCO = 1000.0
        binding.progressCO.progress = ((co / maxCO).coerceIn(0.0, 1.0) * 1000).toInt()

        when {
            co <= 300 -> setCOStatus("LOW", "#2ECC71")
            co <= 500 -> setCOStatus("MID", "#F1C40F")
            else -> setCOStatus("HIGH", "#E74C3C")
        }
    }

    private fun setCOStatus(text: String, colorHex: String) {
        val color = Color.parseColor(colorHex)
        binding.progressCO.progressDrawable.setTint(color)
        binding.keteranganCO.text = text
        binding.keteranganCO.setTextColor(color)
}


    /* ================= HISTORY ================= */

    private fun pushToHistory(co: Double, pm: Double) {
        lifecycleScope.launch(Dispatchers.IO) {

            val date =
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val time =
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            db.child("INA219").get().addOnSuccessListener { inaSnap ->

                val vin =
                    inaSnap.child("VIN").getValue(Double::class.java) ?: 0.0
                val current =
                    inaSnap.child("CURRENT").getValue(Double::class.java) ?: 0.0
                val power =
                    inaSnap.child("POWER").getValue(Double::class.java) ?: 0.0

                val data = mapOf(
                    "CO_PPM" to co.toFloat(),
                    "PM25" to pm.toFloat(),
                    "VIN_V" to vin.toFloat(),
                    "CURRENT_mA" to (current * 1000).toFloat(),
                    "POWER" to power.toFloat()
                )

                db.child("HISTORY").child(date).child(time).setValue(data)
            }
        }
    }
}
