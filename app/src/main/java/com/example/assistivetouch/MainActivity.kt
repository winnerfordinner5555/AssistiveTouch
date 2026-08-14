package com.example.assistivetouch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        status.text = if (Settings.canDrawOverlays(this)) {
            "Overlay permission: granted"
        } else {
            "Overlay permission: not granted yet"
        }
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(64, 120, 64, 64)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, 32, 0, 32)

        val title = TextView(this)
        title.text = "Assistive Touch"
        title.textSize = 26f
        title.setPadding(0, 0, 0, 32)

        val overlayBtn = Button(this)
        overlayBtn.text = "1. Grant the display over other apps permission"
        overlayBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        val accessibilityBtn = Button(this)
        accessibilityBtn.text = "2. Enable the accessibility service"
        accessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val startBtn = Button(this)
        startBtn.text = "3. Start floating button"
        startBtn.setOnClickListener {
            if (Settings.canDrawOverlays(this@MainActivity)) {
                startService(Intent(this@MainActivity, OverlayService::class.java))
                status.text = "Floating button started, you can close this screen."
            } else {
                status.text = "Grant the overlay permission first."
            }
        }

        val stopBtn = Button(this)
        stopBtn.text = "Stop floating button"
        stopBtn.setOnClickListener {
            stopService(Intent(this@MainActivity, OverlayService::class.java))
            status.text = "Floating button stopped."
        }

        root.addView(title)
        root.addView(overlayBtn)
        root.addView(accessibilityBtn)
        root.addView(startBtn)
        root.addView(stopBtn)
        root.addView(status)
        return root
    }
}
