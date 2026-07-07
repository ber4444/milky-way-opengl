package de.hanno_rein.mw

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.PI
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2

/**
 * The GLSurfaceView + gesture forwarding. Mirrors the C++ Android glue: pan
 * (arcball), pinch (zoom), twist (roll), with deltas normalized to dp so feel
 * matches across densities. The render loop drives [ArcballCamera.update] with
 * System.nanoTime() and calls [MilkyWayRenderer.draw].
 */
class MilkyWayView(
    context: Context,
    private val renderer: MilkyWayRenderer,
    private val camera: ArcballCamera,
    private val density: Float,
    private val isTablet: Boolean
) : GLSurfaceView(context) {

    private var prevTwist = 0.0
    private var velX = 0f; private var velY = 0f; private var lastPanMs = 0L

    private val pan = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            camera.pan(0f, 0f, MilkyWayConventions.PHASE_BEGAN)
            lastPanMs = SystemClock.uptimeMillis(); return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            val dxDp = -dX / density; val dyDp = -dY / density
            val now = SystemClock.uptimeMillis(); val dt = (now - lastPanMs).coerceAtLeast(1)
            velX = dxDp / (dt / 1000f); velY = dyDp / (dt / 1000f); lastPanMs = now
            camera.pan(dxDp, dyDp, MilkyWayConventions.PHASE_CHANGED)
            return true
        }
    })

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { camera.pinch(1f, MilkyWayConventions.PHASE_BEGAN); return true }
        override fun onScale(d: ScaleGestureDetector): Boolean { camera.pinch(d.scaleFactor, MilkyWayConventions.PHASE_CHANGED); return true }
        override fun onScaleEnd(d: ScaleGestureDetector) { camera.pinch(1f, MilkyWayConventions.PHASE_ENDED) }
    })

    init {
        setEGLContextClientVersion(2); setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) { renderer.onGlContextCreated() }
            override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
                camera.setViewportWidthDp(w / density)
            }
            override fun onDrawFrame(gl: GL10?) {
                camera.update(System.nanoTime())
                val mv = FloatArray(16); val proj = FloatArray(16)
                camera.modelViewMatrix(mv); camera.projectionMatrix(proj, width.toFloat(), height.toFloat())
                renderer.setFrame(mv, proj, camera.userScale(), width, height)
                // Clear the default framebuffer (the Adreno lesson — never assume zero).
                android.opengl.GLES20.glClearColor(0f, 0f, 0f, 1f)
                android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
                renderer.draw(0)
            }
        })
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scale.onTouchEvent(e); pan.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                camera.pan(0f, 0f, MilkyWayConventions.PHASE_ENDED, velX, velY)
        }
        twist(e); return true
    }

    private fun twist(e: MotionEvent) {
        if (e.pointerCount != 2) {
            if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) { camera.roll(0f, MilkyWayConventions.PHASE_ENDED); prevTwist = 0.0 }
            return
        }
        val a = atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> { prevTwist = a; camera.roll(0f, MilkyWayConventions.PHASE_BEGAN) }
            MotionEvent.ACTION_MOVE -> {
                var d = a - prevTwist
                d = ((d + PI) % (2 * PI) + 2 * PI) % (2 * PI) - PI
                prevTwist = a; camera.roll(-d.toFloat(), MilkyWayConventions.PHASE_CHANGED)
            }
        }
    }
}
