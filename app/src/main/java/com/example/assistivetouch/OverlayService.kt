package com.example.assistivetouch

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
import android.view.View
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

    private var cursorView: FrameLayout? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var tapButtonView: TextView? = null
    private var tapButtonParams: WindowManager.LayoutParams? = null
    private var cursorActive = false

    private val channelId = "assistive_touch_channel"
    private val bubbleSizePx = 130
    private val cursorSizePx = 60

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
        val params = WindowManager.LayoutParams(
            bubbleSizePx, bubbleSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300
        bubbleParams = params

        val bubble = FrameLayout(this)
        val circle = ImageView(this)
        val circleDrawable = GradientDrawable()
        circleDrawable.shape = GradientDrawable.OVAL
        circleDrawable.setColor(Color.parseColor("#3399FF"))
        circle.setImageDrawable(circleDrawable)
        circle.setPadding(10, 10, 10, 10)
        bubble.addView(circle, FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx))
        bubbleView = bubble

        makeDraggable(bubble, bubbleParams) { toggleMenu() }

        windowManager.addView(bubble, bubbleParams)
    }

    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onTap()
                    true
                }
                else -> false
            }
        }
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
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = bubbleParams.x
        menuParams.y = bubbleParams.y + bubbleSizePx + 20

        val menu = LinearLayout(this)
        menu.orientation = LinearLayout.VERTICAL
        menu.setBackgroundColor(Color.parseColor("#DD222222"))
        menu.setPadding(16, 16, 16, 16)

        val cursorLabel = if (cursorActive) "Exit cursor mode" else "Cursor mode"

        val actions: List<Pair<String, () -> Unit>> = listOf(
            "Back" to { performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) },
            "Home" to { performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) },
            "Recents" to { performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) },
            "Notifications" to { performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) },
            "Lock screen" to { lockScreenAction() },
            "Screenshot" to { screenshotAction() },
            "Power menu" to { performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) },
            cursorLabel to { toggleCursorMode() },
            "Close menu" to { removeMenu() }
        )

        for ((label, action) in actions) {
            val item = TextView(this)
            item.text = label
            item.setTextColor(Color.WHITE)
            item.textSize = 15f
            item.setPadding(28, 22, 28, 22)
            item.setOnClickListener {
                action()
                if (label != "Close menu") removeMenu()
            }
            menu.addView(item)
        }

        menuView = menu
        windowManager.addView(menu, menuParams)
    }

    private fun removeMenu() {
        val m = menuView
        if (m != null) {
            windowManager.removeView(m)
            menuView = null
        }
    }

    private fun lockScreenAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Toast.makeText(this, "Requires Android 9 or newer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun screenshotAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            Toast.makeText(this, "Requires Android 9 or newer", Toast.LENGTH_SHORT).show()
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

    private fun toggleCursorMode() {
        if (cursorActive) hideCursor() else showCursor()
    }

    private fun showCursor() {
        if (AssistAccessibilityService.instance == null) {
            Toast.makeText(
                this,
                "Enable the Assistive Touch accessibility service first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        cursorActive = true

        val cParams = WindowManager.LayoutParams(
            cursorSizePx, cursorSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        cParams.gravity = Gravity.TOP or Gravity.START
        cParams.x = 400
        cParams.y = 700
        cursorParams = cParams

        val cursor = FrameLayout(this)
        val ring = ImageView(this)
        val ringDrawable = GradientDrawable()
        ringDrawable.shape = GradientDrawable.OVAL
        ringDrawable.setStroke(6, Color.parseColor("#FF3333"))
        ringDrawable.setColor(Color.TRANSPARENT)
        ring.setImageDrawable(ringDrawable)
        cursor.addView(ring, FrameLayout.LayoutParams(cursorSizePx, cursorSizePx))
        cursorView = cursor

        makeDraggable(cursor, cParams) {
            // tapping the cursor does nothing; dragging moves it
        }

        windowManager.addView(cursor, cParams)

        showTapButton()
    }

    private fun hideCursor() {
        cursorActive = false
        val c = cursorView
        if (c != null) {
            windowManager.removeView(c)
            cursorView = null
        }
        val t = tapButtonView
        if (t != null) {
            windowManager.removeView(t)
            tapButtonView = null
        }
    }

    private fun showTapButton() {
        val tParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        tParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        tParams.y = 160
        tapButtonParams = tParams

        val tapBtn = TextView(this)
        tapBtn.text = "TAP HERE"
        tapBtn.setTextColor(Color.WHITE)
        tapBtn.textSize = 16f
        tapBtn.setPadding(48, 24, 48, 24)
        val bg = GradientDrawable()
        bg.setCornerRadius(60f)
        bg.setColor(Color.parseColor("#DD222222"))
        tapBtn.background = bg

        tapBtn.setOnClickListener {
            val c = cursorParams
            if (c != null) {
                val centerX = (c.x + cursorSizePx / 2).toFloat()
                val centerY = (c.y + cursorSizePx / 2).toFloat()
                AssistAccessibilityService.instance?.performTapAt(centerX, centerY)
            }
        }

        tapButtonView = tapBtn
        windowManager.addView(tapBtn, tParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        val b = bubbleView
        if (b != null) windowManager.removeView(b)
        removeMenu()
        hideCursor()
    }
}
