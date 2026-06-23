package com.aerix.itera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aerix.itera.databinding.ActivityAboutUsBinding

class AboutUsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutUsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutUsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Back button behavior
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
