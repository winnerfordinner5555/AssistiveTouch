package com.example.assistivetouch

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: FrameLayout? = null
    private var menuView: LinearLayout? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private val channelId = "assistive_touch_channel"
    private val bubbleSizePx = 130

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceCompat()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    private fun startForegroundServiceCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Assistive Touch", NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistive Touch is running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun addBubble() {
        bubbleParams = WindowManager.LayoutParams(
            bubbleSizePx, bubbleSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        val bubble = FrameLayout(this)
        val circle = ImageView(this).apply {
            setImageDrawable(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3399FF"))
            })
            setPadding(10, 10, 10, 10)
        }
        bubble.addView(circle, FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx))
        bubbleView = bubble

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) isDragging = true
                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubble, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleMenu()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, bubbleParams)
    }

    private fun toggleMenu() {
        if (menuView != null) removeMenu() else showMenu()
    }

    private fun showMenu() {
        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x
            y = bubbleParams.y + bubbleSizePx + 20
        }

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD222222"))
            setPadding(16, 16, 16, 16)
        }

        val actions: List<Pair<String, () -> Unit>> = listOf(
            "Back" to { performGlobal(AccessibilityService.GLOBAL_ACTION_BACK) },
            "Home" to { performGlobal(AccessibilityService.GLOBAL_ACTION_HOME) },
            "Recents" to { performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS) },
            "Notifications" to { performGlobal(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) },
            "Lock screen" to {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    Toast.makeText(this, "Requires Android 9+", Toast.LENGTH_SHORT).show()
                }
            },
            "Screenshot" to {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobal(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    Toast.makeText(this, "Requires Android 9+", Toast.LENGTH_SHORT).show()
                }
            },
            "Power menu" to { performGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) },
            "Close menu" to { removeMenu() }
        )

        for ((label, action) in actions) {
            val item = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(28, 22, 28, 22)
                setOnClickListener {
                    action()
                    if (label != "Close menu") removeMenu()
                }
            }
            menu.addView(item)
        }

        menuView = menu
        windowManager.addView(menu, menuParams)
    }

    private fun removeMenu() {
        menuView?.let {
            windowManager.removeView(it)
            menuView = null
        }
    }

    private fun performGlobal(action: Int) {
        val service = AssistAccessibilityService.instance
        if (service == null) {
            Toast.makeText(
                this,
                "Enable the Assistive Touch accessibility service first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        service.performGlobalAction(action)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        removeMenu()
    }
}
