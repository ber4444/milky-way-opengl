package de.hanno_rein.mw

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val E = Gl.Enums

/**
 * The 3-pass Milky Way renderer, written entirely against the [Gl] façade —
 * a Kotlin port of the C++ core. Same frame structure as the 2012 app:
 *   1) background quad (milkywaybg.png, premultiplied alpha-over)
 *   2) dust ray-march into a 256² offscreen FBO (PointsMilkyWayToTexture)
 *   3) point-sprite composite over the bg (PointsMilkyWay)
 *
 * Two-phase lifecycle (item 4): [init] is CPU-only (parse catalog, cache bytes);
 * [onGlContextCreated] builds every GL object and is called on every context
 * (re)creation. Camera state lives in [ArcballCamera] and survives context loss.
 */
class MilkyWayRenderer(private val gl: Gl) {
    // ---- CPU-side cached assets (survive context loss) ----
    private var starData = FloatArray(0); private var starCount = 0
    private var pointScale = 1f; private var assetsLoaded = false
    private lateinit var srcQuadV: String; private lateinit var srcQuadF: String
    private lateinit var srcPtsTV: String; private lateinit var srcPtsTF: String
    private lateinit var srcPtsV: String;  private lateinit var srcPtsF: String
    private lateinit var srcSunV: String;  private lateinit var srcSunF: String
    private lateinit var srcOvlV: String;  private lateinit var srcOvlF: String
    private lateinit var pngBg: ByteArray; private lateinit var pngDust: ByteArray
    private lateinit var pngBlob: ByteArray; private lateinit var pngSun: ByteArray

    // ---- GL objects (rebuilt on every context creation) ----
    private var glValid = false
    private var progQuad = 0; private var progPtsT = 0; private var progPts = 0
    private var progSun = 0;  private var progOvl = 0
    private var vboStar = 0;  private var vboQuad = 0
    private var texBg = 0; private var texDust = 0; private var texBlob = 0; private var texSun = 0
    private var fboDust = 0; private var fboDustTex = 0

    // ---- per-frame inputs ----
    private val mv = FloatArray(16); private val proj = FloatArray(16)
    private var userScale = 30f; private var viewW = 0; private var viewH = 0

    // ---- overlay toggles ----
    var annotationsEnabled = false
    var wondersOverlay = false

    /** Phase 1: CPU-only. Load + parse the catalog, cache shader/texture bytes. */
    fun init(assets: AssetProvider, density: Float, isTablet: Boolean): Boolean {
        pointScale = (if (density >= MilkyWayConventions.POINT_SIZE_PREMULTIPLY_DENSITY) MilkyWayConventions.POINT_SIZE_PREMULTIPLY_DENSITY else 1f) *
            (if (isTablet) MilkyWayConventions.POINT_SIZE_PREMULTIPLY_TABLET else 1f)
        srcQuadV = assets.loadText("QuadMilkyWay.vsh");   srcQuadF = assets.loadText("QuadMilkyWay.fsh")
        srcPtsTV = assets.loadText("PointsMilkyWayToTexture.vsh"); srcPtsTF = assets.loadText("PointsMilkyWayToTexture.fsh")
        srcPtsV = assets.loadText("PointsMilkyWay.vsh");   srcPtsF = assets.loadText("PointsMilkyWay.fsh")
        srcSunV = assets.loadText("SunMarker.vsh");        srcSunF = assets.loadText("SunMarker.fsh")
        srcOvlV = assets.loadText("OverlayLine.vsh");      srcOvlF = assets.loadText("OverlayLine.fsh")
        pngBg   = assets.loadBytes("milkywaybg.png")
        pngDust = assets.loadBytes("milkywaydust.png")
        pngBlob = assets.loadBytes("newblob.png")
        pngSun  = assets.loadBytes("sun_marker.png")
        val (s, n) = StarCatalog.load(assets.loadBytes("milkyway0.binary"))
        starData = s; starCount = n
        assetsLoaded = srcQuadV.isNotEmpty() && srcPtsV.isNotEmpty()
        return assetsLoaded
    }

    /** Phase 2: build GL objects. Repeatable; context-loss safe. */
    fun onGlContextCreated(): Boolean {
        if (!assetsLoaded) return false
        glValid = false
        println("[renderer] GL_RENDERER = ${gl.getString(E.GL_RENDERER)}")
        println("[renderer] GL_VERSION  = ${gl.getString(E.GL_VERSION)}")
        progQuad = gl.createProgram(srcQuadV, srcQuadF)
        progPtsT = gl.createProgram(srcPtsTV, srcPtsTF)
        progPts = gl.createProgram(srcPtsV, srcPtsF)
        progSun = gl.createProgram(srcSunV, srcSunF)
        progOvl = gl.createProgram(srcOvlV, srcOvlF)
        texBg = uploadPng(pngBg, premultiply = true)
        texDust = uploadPng(pngDust, premultiply = false)
        texBlob = uploadPng(pngBlob, premultiply = true)
        texSun = uploadPng(pngSun, premultiply = true)

        vboStar = gl.createBuffer(); gl.bindBuffer(E.GL_ARRAY_BUFFER, vboStar)
        gl.bufferData(E.GL_ARRAY_BUFFER, starData, E.GL_STATIC_DRAW); gl.bindBuffer(E.GL_ARRAY_BUFFER, 0)
        val quadVerts = floatArrayOf(
            -0.5f,-0.5f,0f, 0f,0f,  -0.5f,0.5f,0f, 0f,1f,  0.5f,-0.5f,0f, 1f,0f,  0.5f,0.5f,0f, 1f,1f
        )
        vboQuad = gl.createBuffer(); gl.bindBuffer(E.GL_ARRAY_BUFFER, vboQuad)
        gl.bufferData(E.GL_ARRAY_BUFFER, quadVerts, E.GL_STATIC_DRAW); gl.bindBuffer(E.GL_ARRAY_BUFFER, 0)
        buildDustFbo()
        glValid = true
        return true
    }

    private fun buildDustFbo() {
        fboDust = gl.createFramebuffer(); gl.bindFramebuffer(E.GL_FRAMEBUFFER, fboDust)
        fboDustTex = gl.createTexture(); gl.bindTexture(E.GL_TEXTURE_2D, fboDustTex)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_WRAP_S, E.GL_CLAMP_TO_EDGE)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_WRAP_T, E.GL_CLAMP_TO_EDGE)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_MAG_FILTER, E.GL_NEAREST)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_MIN_FILTER, E.GL_NEAREST)
        gl.texImage2D(E.GL_TEXTURE_2D, 0, E.GL_RGBA, 256, 256, 0, E.GL_RGBA, E.GL_UNSIGNED_BYTE, null)
        gl.framebufferTexture2D(E.GL_FRAMEBUFFER, E.GL_COLOR_ATTACHMENT0, E.GL_TEXTURE_2D, fboDustTex, 0)
        gl.bindTexture(E.GL_TEXTURE_2D, 0); gl.bindFramebuffer(E.GL_FRAMEBUFFER, 0)
    }

    private fun uploadPng(png: ByteArray, premultiply: Boolean): Int {
        val dec = decodePng(png)
        val rgba = dec.rgba

        // Grayscale sources (newblob.png, milkywaydust.png) must route luminance
        // to alpha. The original 2012 app's Texture2D detected grayscale and used
        // GL_ALPHA; BitmapFactory/CGImage expand grayscale to RGBA with A=255
        // (opaque), which makes the shader's .a sample uniformly 255 — solid
        // square sprites instead of soft circular blobs. Fix: detect by checking
        // if R==G==B==A for every pixel (the hallmark of a grayscale-expanded
        // image where the original luminance was copied to all 4 channels).
        if (dec.n <= 2 || isGrayscaleExpandedToRGBA(rgba)) {
            var i = 0
            while (i < rgba.size) {
                val lum = rgba[i] // R==G==B for grayscale-expanded, so R is luminance
                rgba[i] = 0; rgba[i + 1] = 0; rgba[i + 2] = 0; rgba[i + 3] = lum
                i += 4
            }
        }

        if (premultiply && dec.n >= 4) {
            var i = 0
            while (i < rgba.size) {
                val a = rgba[i + 3].toInt() and 0xFF
                if (a < 255) {
                    val af = a / 255f
                    rgba[i] = (rgba[i].toInt() and 0xFF).times(af).toInt().toByte()
                    rgba[i + 1] = (rgba[i + 1].toInt() and 0xFF).times(af).toInt().toByte()
                    rgba[i + 2] = (rgba[i + 2].toInt() and 0xFF).times(af).toInt().toByte()
                }
                i += 4
            }
        }
        val tex = gl.createTexture(); gl.bindTexture(E.GL_TEXTURE_2D, tex)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_WRAP_S, E.GL_REPEAT)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_WRAP_T, E.GL_REPEAT)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_MIN_FILTER, E.GL_LINEAR_MIPMAP_LINEAR)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_MAG_FILTER, E.GL_LINEAR)
        gl.texImage2D(E.GL_TEXTURE_2D, 0, E.GL_RGBA, dec.w, dec.h, 0, E.GL_RGBA, E.GL_UNSIGNED_BYTE, rgba)
        gl.generateMipmap(E.GL_TEXTURE_2D); gl.bindTexture(E.GL_TEXTURE_2D, 0)
        return tex
    }

    /** Detect whether an RGBA byte array is a grayscale image expanded to RGBA
     *  (R==G==B for all pixels). This catches the BitmapFactory/CGImage decoding
     *  of grayscale PNGs, which copy luminance to RGB and set A=255. */
    private fun isGrayscaleExpandedToRGBA(rgba: ByteArray): Boolean {
        // Sample a subset for speed (large textures don't need every pixel checked).
        val stride = if (rgba.size > 4096) 16 else 4
        var i = 0
        while (i < rgba.size) {
            if (rgba[i] != rgba[i + 1] || rgba[i + 1] != rgba[i + 2]) return false
            i += stride
        }
        return true
    }

    fun setFrame(modelView: FloatArray, projection: FloatArray, scale: Float, w: Int, h: Int) {
        modelView.copyInto(mv)
        projection.copyInto(proj)
        userScale = scale; viewW = w; viewH = h
    }

    fun draw(defaultFbo: Int) {
        if (!glValid) return
        // ---- tilt ----
        val zAxis = FloatArray(3); Math.mat4MultiplyVector3(zAxis, mv, floatArrayOf(0f, 0f, 1f))
        val z = zAxis[2]

        // ===== Pass 1: background quad =====
        var op = 1.1f * abs(z) - 0.2f; op = op.coerceIn(0f, 1f)
        op *= -1f + ln(userScale * 1.3f).toFloat(); op = op.coerceIn(0f, 1f)
        gl.bindFramebuffer(E.GL_FRAMEBUFFER, defaultFbo)
        gl.viewport(0, 0, viewW, viewH)
        gl.enable(E.GL_BLEND); gl.blendFunc(E.GL_ONE, E.GL_ONE_MINUS_SRC_ALPHA)
        val sh = progQuad; gl.useProgram(sh)
        gl.activeTexture(E.GL_TEXTURE0); gl.bindTexture(E.GL_TEXTURE_2D, texBg)
        val mvTex = FloatArray(16); mv.copyInto(mvTex)
        val sc = FloatArray(16)
        Math.mat4Scale(sc, MilkyWayConventions.MILKY_WAY_SCALE_KPC, MilkyWayConventions.MILKY_WAY_SCALE_KPC, MilkyWayConventions.MILKY_WAY_SCALE_KPC)
        val tmp = FloatArray(16); Math.mat4Multiply(tmp, mvTex, sc); tmp.copyInto(mvTex)
        val aPos = gl.attribLocation(sh, "Position"); val aTex = gl.attribLocation(sh, "TextureCoord")
        gl.uniformMatrix4fv(gl.uniformLocation(sh, "modelViewMatrix"), 1, false, mvTex)
        gl.uniformMatrix4fv(gl.uniformLocation(sh, "projectionMatrix"), 1, false, proj)
        gl.uniform1i(gl.uniformLocation(sh, "Sampler"), 0)
        gl.uniform1f(gl.uniformLocation(sh, "Opacity"), op)
        gl.bindBuffer(E.GL_ARRAY_BUFFER, vboQuad)
        gl.enableVertexAttribArray(aPos); gl.enableVertexAttribArray(aTex)
        gl.vertexAttribPointer(aPos, 3, E.GL_FLOAT, false, 20, 0)
        gl.vertexAttribPointer(aTex, 2, E.GL_FLOAT, false, 20, 12)
        gl.drawArrays(E.GL_TRIANGLE_STRIP, 0, 4)
        gl.disableVertexAttribArray(aPos); gl.disableVertexAttribArray(aTex)
        gl.bindBuffer(E.GL_ARRAY_BUFFER, 0); gl.bindTexture(E.GL_TEXTURE_2D, 0); gl.disable(E.GL_BLEND)

        // ===== Pass 2: dust FBO =====
        gl.bindFramebuffer(E.GL_FRAMEBUFFER, fboDust); gl.viewport(0, 0, 256, 256)
        gl.clearColor(0f, 0f, 0f, 1f); gl.clear(E.GL_COLOR_BUFFER_BIT)
        val sh2 = progPtsT; gl.useProgram(sh2); gl.disable(E.GL_BLEND)
        gl.activeTexture(E.GL_TEXTURE0); gl.bindTexture(E.GL_TEXTURE_2D, texDust)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_WRAP_S, E.GL_CLAMP_TO_EDGE)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_WRAP_T, E.GL_CLAMP_TO_EDGE)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_MAG_FILTER, E.GL_LINEAR)
        gl.texParameteri(E.GL_TEXTURE_2D, E.GL_TEXTURE_MIN_FILTER, E.GL_LINEAR)
        val inv = FloatArray(16); Math.mat4Invert(inv, mv)
        val camPos = FloatArray(3); Math.mat4MultiplyAndProjectVector3(camPos, inv, floatArrayOf(0f, 0f, userScale))
        gl.uniformMatrix4fv(gl.uniformLocation(sh2, "modelViewMatrix"), 1, false, mv)
        gl.uniform3fv(gl.uniformLocation(sh2, "cameraPosition"), camPos)
        gl.uniform1i(gl.uniformLocation(sh2, "SamplerDust"), 0)
        val aP2 = gl.attribLocation(sh2, "Position"); val aT2 = gl.attribLocation(sh2, "texCoordIn")
        gl.bindBuffer(E.GL_ARRAY_BUFFER, vboStar)
        gl.vertexAttribPointer(aP2, 3, E.GL_FLOAT, false, 40, 0)
        gl.vertexAttribPointer(aT2, 2, E.GL_FLOAT, false, 40, 32)
        gl.enableVertexAttribArray(aP2); gl.enableVertexAttribArray(aT2)
        gl.drawArrays(E.GL_POINTS, 0, starCount)
        gl.bindTexture(E.GL_TEXTURE_2D, 0); gl.bindBuffer(E.GL_ARRAY_BUFFER, 0)
        gl.disableVertexAttribArray(aT2); gl.disableVertexAttribArray(aP2)

        // ===== Pass 3: point composite =====
        gl.bindFramebuffer(E.GL_FRAMEBUFFER, defaultFbo)
        gl.viewport(0, 0, viewW, viewH)
        val sh3 = progPts; gl.useProgram(sh3); gl.enable(E.GL_BLEND)
        gl.blendFunc(E.GL_SRC_ALPHA, E.GL_ONE_MINUS_SRC_ALPHA)
        gl.uniform1i(gl.uniformLocation(sh3, "SamplerDust"), 1)
        gl.uniform1i(gl.uniformLocation(sh3, "Sampler"), 0)
        gl.activeTexture(E.GL_TEXTURE1); gl.bindTexture(E.GL_TEXTURE_2D, fboDustTex)
        gl.activeTexture(E.GL_TEXTURE0); gl.bindTexture(E.GL_TEXTURE_2D, texBlob)
        gl.uniformMatrix4fv(gl.uniformLocation(sh3, "modelViewMatrix"), 1, false, mv)
        gl.uniformMatrix4fv(gl.uniformLocation(sh3, "projectionMatrix"), 1, false, proj)
        gl.uniform1f(gl.uniformLocation(sh3, "UserScale"), userScale)
        var psz = minOf(16f / userScale, 8f / sqrt(userScale)) * pointScale
        gl.uniform1f(gl.uniformLocation(sh3, "pointSizePreMultiply"), psz)
        val aP3 = gl.attribLocation(sh3, "Position"); val aS3 = gl.attribLocation(sh3, "PointSize")
        val aC3 = gl.attribLocation(sh3, "Color"); val aT3 = gl.attribLocation(sh3, "texCoordIn")
        gl.bindBuffer(E.GL_ARRAY_BUFFER, vboStar)
        gl.vertexAttribPointer(aP3, 3, E.GL_FLOAT, false, 40, 0)
        gl.vertexAttribPointer(aS3, 1, E.GL_FLOAT, false, 40, 12)
        gl.vertexAttribPointer(aC3, 4, E.GL_FLOAT, false, 40, 16)
        gl.vertexAttribPointer(aT3, 2, E.GL_FLOAT, false, 40, 32)
        gl.enableVertexAttribArray(aP3); gl.enableVertexAttribArray(aS3)
        gl.enableVertexAttribArray(aC3); gl.enableVertexAttribArray(aT3)
        gl.drawArrays(E.GL_POINTS, 0, starCount)
        gl.activeTexture(E.GL_TEXTURE1); gl.bindTexture(E.GL_TEXTURE_2D, 0)
        gl.activeTexture(E.GL_TEXTURE0); gl.bindTexture(E.GL_TEXTURE_2D, 0)
        gl.bindBuffer(E.GL_ARRAY_BUFFER, 0)
        gl.disableVertexAttribArray(aP3); gl.disableVertexAttribArray(aS3)
        gl.disableVertexAttribArray(aC3); gl.disableVertexAttribArray(aT3)
        gl.disable(E.GL_BLEND)

        if (annotationsEnabled) drawSunMarker()
        if (wondersOverlay) drawWonders()
    }

    private fun drawSunMarker() {
        val sh = progSun
        if (sh == 0) return
        val r = (MilkyWayConventions.SUN_GALACTOCENTRIC_RADIUS_KPC / MilkyWayConventions.KPC_PER_MODEL_UNIT).toFloat()
        val az = MilkyWayConventions.BAR_ANGLE_RAD + MilkyWayConventions.SUN_AZIMUTH_OFFSET_FROM_BAR_RAD
        val pos = floatArrayOf(r * cos(az), r * sin(az), 0f)
        gl.bindFramebuffer(E.GL_FRAMEBUFFER, 0); gl.viewport(0, 0, viewW, viewH)
        gl.useProgram(sh); gl.enable(E.GL_BLEND); gl.blendFunc(E.GL_ONE, E.GL_ONE_MINUS_SRC_ALPHA)
        gl.activeTexture(E.GL_TEXTURE0); gl.bindTexture(E.GL_TEXTURE_2D, texSun)
        gl.uniformMatrix4fv(gl.uniformLocation(sh, "modelViewMatrix"), 1, false, mv)
        gl.uniformMatrix4fv(gl.uniformLocation(sh, "projectionMatrix"), 1, false, proj)
        gl.uniform1i(gl.uniformLocation(sh, "Sampler"), 0)
        gl.uniform1f(gl.uniformLocation(sh, "pointSize"), minOf(viewW, viewH) * 0.09f)
        val aP = gl.attribLocation(sh, "Position")
        gl.enableVertexAttribArray(aP)
        // single-vertex attrib pointer into a transient array — façade handles pinning
        gl.bindBuffer(E.GL_ARRAY_BUFFER, 0)
        gl.vertexAttribPointerDirect(aP, 3, pos)
        gl.drawArrays(E.GL_POINTS, 0, 1)
        gl.disableVertexAttribArray(aP); gl.bindTexture(E.GL_TEXTURE_2D, 0); gl.disable(E.GL_BLEND)
    }

    private fun drawWonders() {
        val conv = MilkyWayConventions.KPC_PER_MODEL_UNIT.toFloat()
        val rIn = MilkyWayConventions.HAB_ZONE_INNER_KPC / conv
        val rOut = MilkyWayConventions.HAB_ZONE_OUTER_KPC / conv
        val seg = 96
        // Inner ring (blue), outer ring (green), bar (amber line).
        drawRing(rIn, floatArrayOf(0.30f, 0.75f, 1.0f, 0.75f), seg)
        drawRing(rOut, floatArrayOf(0.40f, 1.0f, 0.60f, 0.75f), seg)
        val half = (MilkyWayConventions.BAR_HALF_LENGTH_KPC / conv).toFloat()
        val ba = MilkyWayConventions.BAR_ANGLE_RAD
        drawOverlayLine(floatArrayOf(-half*cos(ba),-half*sin(ba),0f, half*cos(ba),half*sin(ba),0f), 2,
            floatArrayOf(1.0f, 0.78f, 0.30f, 0.80f), E.GL_LINES)
    }

    private fun drawRing(r: Float, color: FloatArray, seg: Int) {
        val pts = FloatArray((seg + 1) * 3)
        for (i in 0..seg) {
            val a = i.toFloat() / seg * 2f * 3.14159265358979323846f
            pts[i*3] = r * cos(a); pts[i*3+1] = r * sin(a); pts[i*3+2] = 0f
        }
        drawOverlayLine(pts, seg + 1, color, E.GL_LINE_STRIP)
    }

    private fun drawOverlayLine(verts: FloatArray, count: Int, color: FloatArray, mode: Int) {
        val sh = progOvl
        if (sh == 0) return
        gl.bindFramebuffer(E.GL_FRAMEBUFFER, 0); gl.viewport(0, 0, viewW, viewH)
        gl.useProgram(sh); gl.enable(E.GL_BLEND); gl.blendFunc(E.GL_ONE, E.GL_ONE_MINUS_SRC_ALPHA)
        gl.uniformMatrix4fv(gl.uniformLocation(sh, "modelViewMatrix"), 1, false, mv)
        gl.uniformMatrix4fv(gl.uniformLocation(sh, "projectionMatrix"), 1, false, proj)
        gl.uniform4fv(gl.uniformLocation(sh, "Color"), color)
        val aP = gl.attribLocation(sh, "Position")
        gl.enableVertexAttribArray(aP); gl.bindBuffer(E.GL_ARRAY_BUFFER, 0)
        gl.vertexAttribPointerDirect(aP, 3, verts)
        gl.drawArrays(mode, 0, count)
        gl.disableVertexAttribArray(aP); gl.disable(E.GL_BLEND)
    }
}
