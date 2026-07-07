package de.hanno_rein.mw

import android.app.Activity
import android.content.res.AssetManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2

/**
 * Android host for the KMP renderer — the thin glue that owns the GLSurfaceView,
 * forwards gestures to the shared [ArcballCamera], and hosts the overlay toggles.
 * All rendering logic lives in `:renderer` commonMain; this only contributes a
 * surface, a context, and a finger (the article's thesis).
 */
class MainActivity : Activity() {
    private lateinit var surfaceView: MilkyWayView
    private lateinit var camera: ArcballCamera
    private lateinit var renderer: MilkyWayRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        val dm = resources.displayMetrics
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        camera = ArcballCamera()
        renderer = MilkyWayRenderer(GlAndroid())
        surfaceView = MilkyWayView(this, renderer, camera, dm.density, isTablet)
        // CPU-only init (parse catalog, cache bytes) before the GL context exists.
        renderer.init(AndroidAssetProvider(assets), dm.density, isTablet)

        val root = FrameLayout(this).apply { addView(surfaceView, FrameLayout.LayoutParams(-1, -1)) }
        // Overlay toggles (same UX as the C++ build).
        val caption = TextView(this).apply {
            setTextColor(Color.parseColor("#E8E8F0")); textSize = 11f
            setPadding(24, 12, 24, 12); setBackgroundColor(0x99000000.toInt())
            visibility = android.view.View.GONE
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val controls = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = 48; marginStart = 24 }
        }
        val sunBtn = overlayButton(" ⊙ Sun ")
        sunBtn.setOnClickListener {
            renderer.annotationsEnabled = !renderer.annotationsEnabled
            updateCaption(caption, renderer)
        }
        row.addView(sunBtn)
        renderer.wonderNames().forEach { name ->
            val wonderBtn = overlayButton(" ✦ $name ")
            wonderBtn.setOnClickListener {
                renderer.toggleWonder(name)
                updateCaption(caption, renderer)
            }
            row.addView(wonderBtn)
        }
        controls.addView(row)
        root.addView(controls); root.addView(caption)
        setContentView(root)
    }

    private fun overlayButton(text: String) = Button(this).apply {
        this.text = text; setBackgroundColor(0x66000000); setTextColor(Color.WHITE)
    }

    private fun updateCaption(caption: TextView, r: MilkyWayRenderer) {
        val lines = mutableListOf<String>()
        if (r.annotationsEnabled) lines += "☉ Our address: ~8.2 kpc from the galactic center, on the Local (Orion) Spur."
        r.selectedWonderCaption()?.let { lines += it }
        caption.text = lines.joinToString("\n\n")
        caption.visibility = if (lines.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    override fun onPause() { super.onPause(); surfaceView.onPause() }
    override fun onResume() { super.onResume(); surfaceView.onResume() }
}
