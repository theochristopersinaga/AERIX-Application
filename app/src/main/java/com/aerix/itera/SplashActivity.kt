package com.aerix.itera

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val handler = Handler(Looper.getMainLooper())
        var progressStatus = 0

        Thread {
            while (progressStatus < 100) {
                progressStatus++
                handler.post {
                    progressBar.progress = progressStatus
                }
                Thread.sleep(30)
            }

            handler.post {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }.start()
    }
}
