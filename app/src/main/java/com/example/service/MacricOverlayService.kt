package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ProfileRepository
import com.example.model.CrosshairConfig
import com.example.model.CrosshairStyle
import android.graphics.Canvas
import android.graphics.Paint

class MacricOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayAttached = false

    companion object {
        const val ACTION_START_OVERLAY = "com.example.macric.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.example.macric.ACTION_STOP_OVERLAY"
        const val ACTION_UPDATE_CONFIG = "com.example.macric.ACTION_UPDATE_CONFIG"
        private const val CHANNEL_ID = "macric_overlay_channel"
        private const val NOTIF_ID = 1001

        var isRunning: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopForegroundOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                updateOverlayContent()
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIF_ID,
                        buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIF_ID, buildNotification())
                }
                isRunning = true
                showFloatingOverlay()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MACRIC Crosshair Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of the active MACRIC HUD crosshair overlay"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MacricOverlayService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MACRIC Overlay Active")
            .setContentText("Crosshair & controls running on screen. Tap to tune.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close Overlay", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingOverlay() {
        if (isOverlayAttached) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        val repo = ProfileRepository(this)
        val connectedGameId = repo.getConnectedGameId()
        val profile = repo.getProfiles().find { it.gameId == connectedGameId }
            ?: repo.getProfiles().firstOrNull()

        val crosshair = profile?.crosshair ?: CrosshairConfig()

        val customCanvas = CrosshairCanvasView(this, crosshair)

        val container = FrameLayout(this).apply {
            addView(customCanvas)
        }

        // Draggable touch listener
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(container, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
                    }
                }
                return false
            }
        })

        overlayView = container
        try {
            windowManager?.addView(container, params)
            isOverlayAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayContent() {
        if (!isOverlayAttached || overlayView == null) return
        val repo = ProfileRepository(this)
        val connectedGameId = repo.getConnectedGameId()
        val profile = repo.getProfiles().find { it.gameId == connectedGameId }
            ?: repo.getProfiles().firstOrNull()
        val crosshair = profile?.crosshair ?: CrosshairConfig()

        val container = overlayView as? FrameLayout ?: return
        container.removeAllViews()
        val customCanvas = CrosshairCanvasView(this, crosshair)
        container.addView(customCanvas)
    }

    private fun stopForegroundOverlay() {
        isRunning = false
        if (isOverlayAttached && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            isOverlayAttached = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundOverlay()
        super.onDestroy()
    }

    // Custom view that renders the crosshair based on CrosshairConfig
    class CrosshairCanvasView(context: Context, private val config: CrosshairConfig) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try {
                Color.parseColor(config.colorHex)
            } catch (e: Exception) {
                Color.parseColor("#9312FF")
            }
            alpha = (config.opacity * 255).toInt().coerceIn(20, 255)
            strokeWidth = config.thickness * resources.displayMetrics.density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.color
            alpha = paint.alpha
            style = Paint.Style.FILL
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val d = (config.size * resources.displayMetrics.density * 2.2f).toInt().coerceAtLeast(64)
            setMeasuredDimension(d, d)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!config.enabled) return

            val cx = width / 2f
            val cy = height / 2f
            val s = config.size * resources.displayMetrics.density * 0.5f
            val g = config.gap * resources.displayMetrics.density * 0.4f
            val t = config.thickness * resources.displayMetrics.density

            when (config.style) {
                CrosshairStyle.DOT -> {
                    canvas.drawCircle(cx, cy, (t * 1.5f).coerceAtLeast(4f), fillPaint)
                }
                CrosshairStyle.PLUS -> {
                    canvas.drawLine(cx - s, cy, cx + s, cy, paint)
                    canvas.drawLine(cx, cy - s, cx, cy + s, paint)
                }
                CrosshairStyle.CIRCLE -> {
                    canvas.drawCircle(cx, cy, s * 0.75f, paint)
                }
                CrosshairStyle.CROSS_GAP -> {
                    canvas.drawLine(cx - s, cy, cx - g, cy, paint)
                    canvas.drawLine(cx + g, cy, cx + s, cy, paint)
                    canvas.drawLine(cx, cy - s, cx, cy - g, paint)
                    canvas.drawLine(cx, cy + g, cx, cy + s, paint)
                }
                CrosshairStyle.CIRCLE_DOT -> {
                    canvas.drawCircle(cx, cy, s * 0.8f, paint)
                    canvas.drawCircle(cx, cy, (t * 1.3f).coerceAtLeast(3f), fillPaint)
                }
                CrosshairStyle.T_SHAPE -> {
                    canvas.drawLine(cx - s, cy, cx - g, cy, paint)
                    canvas.drawLine(cx + g, cy, cx + s, cy, paint)
                    canvas.drawLine(cx, cy + g, cx, cy + s, paint)
                }
                CrosshairStyle.CHEVRON -> {
                    canvas.drawLine(cx - s * 0.7f, cy + s * 0.4f, cx, cy - s * 0.4f, paint)
                    canvas.drawLine(cx, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f, paint)
                }
                CrosshairStyle.DIAMOND -> {
                    canvas.drawLine(cx, cy - s * 0.7f, cx + s * 0.7f, cy, paint)
                    canvas.drawLine(cx + s * 0.7f, cy, cx, cy + s * 0.7f, paint)
                    canvas.drawLine(cx, cy + s * 0.7f, cx - s * 0.7f, cy, paint)
                    canvas.drawLine(cx - s * 0.7f, cy, cx, cy - s * 0.7f, paint)
                }
            }
        }
    }
}
