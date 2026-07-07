package de.hanno_rein.mw

/**
 * The GL façade — the single seam between shared Kotlin and the platform GL
 * backend. This is what replaces "verbatim-shared GL" from the C++ design.
 * ~30 calls covering the exact ES2 slice the renderer touches; nothing more.
 *
 * The shared [MilkyWayRenderer] programs entirely against this interface.
 * `androidMain` implements it over `GLES20`; `iosMain` over Kotlin/Native GL.
 * Handles ([GlBuffer], [GlTexture], etc.) are opaque value types so the shared
 * code never sees a raw GL integer name — and the premultiplied-alpha /
 * explicit-clear lessons from the C++ port are encoded as named methods
 * (`blendPremultiplied`, `clearColor`), not left to per-platform discipline.
 */
interface Gl {
    // GL object handles are plain Int names on both platforms (GLES20 uses Int,
    // KN's cinterop exposes GLuint as Int after toInt()). Keeping them as Int
    // avoids value-class/expect-actual complexity; the shared code never
    // interprets them, only passes them back to the façade.
    fun createProgram(vertexSrc: String, fragmentSrc: String): Int
    fun useProgram(p: Int)
    fun attribLocation(p: Int, name: String): Int
    fun uniformLocation(p: Int, name: String): Int
    fun uniformMatrix4fv(loc: Int, count: Int, transpose: Boolean, value: FloatArray)
    fun uniform1i(loc: Int, v: Int)
    fun uniform1f(loc: Int, v: Float)
    fun uniform3fv(loc: Int, v: FloatArray)
    fun uniform4fv(loc: Int, v: FloatArray)

    // ---- buffers ----
    fun createBuffer(): Int
    fun bufferData(target: Int, data: FloatArray, usage: Int)
    fun bindBuffer(target: Int, b: Int)
    fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int)
    fun vertexAttribPointerDirect(index: Int, size: Int, data: FloatArray, stride: Int = size * 4, offset: Int = 0)
    fun enableVertexAttribArray(index: Int)
    fun disableVertexAttribArray(index: Int)

    // ---- textures ----
    fun createTexture(): Int
    fun bindTexture(target: Int, t: Int)
    fun activeTexture(unit: Int)
    fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: ByteArray?)
    fun texParameteri(target: Int, pname: Int, value: Int)
    fun generateMipmap(target: Int)
    fun texSubImage2D(target: Int, level: Int, x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, data: ByteArray?)

    // ---- framebuffers ----
    fun createFramebuffer(): Int
    fun bindFramebuffer(target: Int, f: Int)
    fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, t: Int, level: Int)
    fun checkFramebufferStatus(target: Int): Int

    // ---- state / draw ----
    fun viewport(x: Int, y: Int, w: Int, h: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun blendFunc(sfactor: Int, dfactor: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun getString(name: Int): String

    // ---- GL constants the renderer references (declared once on the façade).
    // Prefixed with GL_ to avoid collisions with C preprocessor macros (TRUE,
    // FALSE, ONE are commonly #define'd in system headers, which breaks the
    // generated Obj-C framework header).
    object Enums {
        const val GL_TEXTURE_2D = 0x0DE1
        const val GL_TEXTURE0 = 0x84C0
        const val GL_TEXTURE1 = 0x84C1
        const val GL_TEXTURE_WRAP_S = 0x2802
        const val GL_TEXTURE_WRAP_T = 0x2803
        const val GL_TEXTURE_MIN_FILTER = 0x2801
        const val GL_TEXTURE_MAG_FILTER = 0x2800
        const val GL_LINEAR = 0x2601
        const val GL_NEAREST = 0x2600
        const val GL_LINEAR_MIPMAP_LINEAR = 0x2703
        const val GL_REPEAT = 0x2901
        const val GL_CLAMP_TO_EDGE = 0x812F
        const val GL_ARRAY_BUFFER = 0x8892
        const val GL_STATIC_DRAW = 0x88E4
        const val GL_FRAMEBUFFER = 0x8D40
        const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
        const val GL_TRIANGLE_STRIP = 0x0005
        const val GL_POINTS = 0x0000
        const val GL_LINES = 0x0001
        const val GL_LINE_STRIP = 0x0003
        const val GL_BLEND = 0x0BE2
        const val GL_SRC_ALPHA = 0x0302
        const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
        const val GL_ONE = 0x1
        const val GL_COLOR_BUFFER_BIT = 0x4000
        const val GL_FLOAT = 0x1406
        const val GL_UNSIGNED_BYTE = 0x1401
        const val GL_RGBA = 0x1908
        const val GL_FALSE_B = 0
        const val GL_TRUE_B = 1
        const val GL_RENDERER = 0x1F01
        const val GL_VERSION = 0x1F02
    }
}
