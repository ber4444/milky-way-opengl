package de.hanno_rein.mw

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Android actual of [Gl] — delegates to [GLES20]. Handles are plain Int. */
class GlAndroid : Gl {
    override fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { error("program link failed: ${GLES20.glGetProgramInfoLog(p)}") }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        return p
    }
    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { GLES20.glDeleteShader(s); error("shader compile: ${GLES20.glGetShaderInfoLog(s)}") }
        return s
    }
    override fun useProgram(p: Int) = GLES20.glUseProgram(p)
    override fun attribLocation(p: Int, name: String): Int = GLES20.glGetAttribLocation(p, name)
    override fun uniformLocation(p: Int, name: String): Int = GLES20.glGetUniformLocation(p, name)
    override fun uniformMatrix4fv(loc: Int, count: Int, transpose: Boolean, value: FloatArray) =
        GLES20.glUniformMatrix4fv(loc, count, transpose, value, 0)
    override fun uniform1i(loc: Int, v: Int) = GLES20.glUniform1i(loc, v)
    override fun uniform1f(loc: Int, v: Float) = GLES20.glUniform1f(loc, v)
    override fun uniform3fv(loc: Int, v: FloatArray) = GLES20.glUniform3fv(loc, 1, v, 0)
    override fun uniform4fv(loc: Int, v: FloatArray) = GLES20.glUniform4fv(loc, 1, v, 0)
    override fun createBuffer(): Int { val a = IntArray(1); GLES20.glGenBuffers(1, a, 0); return a[0] }
    override fun bufferData(target: Int, data: FloatArray, usage: Int) =
        GLES20.glBufferData(target, data.size * 4, data.toDirectBuffer(), usage)
    override fun bindBuffer(target: Int, b: Int) = GLES20.glBindBuffer(target, b)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        GLES20.glVertexAttribPointer(index, size, type, normalized, stride, offset)
    override fun vertexAttribPointerDirect(index: Int, size: Int, data: FloatArray, stride: Int, offset: Int) =
        GLES20.glVertexAttribPointer(index, size, GLES20.GL_FLOAT, false, stride, data.toDirectBuffer().position(offset / 4))
    override fun enableVertexAttribArray(index: Int) = GLES20.glEnableVertexAttribArray(index)
    override fun disableVertexAttribArray(index: Int) = GLES20.glDisableVertexAttribArray(index)
    override fun createTexture(): Int { val a = IntArray(1); GLES20.glGenTextures(1, a, 0); return a[0] }
    override fun bindTexture(target: Int, t: Int) = GLES20.glBindTexture(target, t)
    override fun activeTexture(unit: Int) = GLES20.glActiveTexture(unit)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: ByteArray?) {
        val buf = data?.let { ByteBuffer.allocateDirect(it.size).order(ByteOrder.nativeOrder()).put(it).position(0) }
        GLES20.glTexImage2D(target, level, internalFormat, w, h, border, format, type, buf)
    }
    override fun texParameteri(target: Int, pname: Int, value: Int) = GLES20.glTexParameteri(target, pname, value)
    override fun generateMipmap(target: Int) = GLES20.glGenerateMipmap(target)
    override fun texSubImage2D(target: Int, level: Int, x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, data: ByteArray?) {
        val buf = data?.let { ByteBuffer.allocateDirect(it.size).order(ByteOrder.nativeOrder()).put(it).position(0) }
        GLES20.glTexSubImage2D(target, level, x, y, w, h, format, type, buf)
    }
    override fun createFramebuffer(): Int { val a = IntArray(1); GLES20.glGenFramebuffers(1, a, 0); return a[0] }
    override fun bindFramebuffer(target: Int, f: Int) = GLES20.glBindFramebuffer(target, f)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, t: Int, level: Int) =
        GLES20.glFramebufferTexture2D(target, attachment, texTarget, t, level)
    override fun checkFramebufferStatus(target: Int): Int = GLES20.glCheckFramebufferStatus(target)
    override fun viewport(x: Int, y: Int, w: Int, h: Int) = GLES20.glViewport(x, y, w, h)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES20.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES20.glClear(mask)
    override fun enable(cap: Int) = GLES20.glEnable(cap)
    override fun disable(cap: Int) = GLES20.glDisable(cap)
    override fun blendFunc(sfactor: Int, dfactor: Int) = GLES20.glBlendFunc(sfactor, dfactor)
    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES20.glDrawArrays(mode, first, count)
    override fun getString(name: Int): String = GLES20.glGetString(name) ?: "(null)"
    private fun FloatArray.toDirectBuffer(): FloatBuffer {
        val b = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        b.put(this); b.position(0); return b
    }
}
