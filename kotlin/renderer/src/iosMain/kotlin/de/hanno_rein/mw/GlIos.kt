@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.hanno_rein.mw

import mwgl.*
import kotlinx.cinterop.addressOf

import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

/**
 * iOS actual of [Gl] — delegates to the mwgl_* C shim (built via cinterop from
 * MwGl.def), which wraps OpenGLES.framework with clean C types. This sidesteps
 * the CPointer<ByteVarOf<Byte>> type maze that raw cinterop GL stubs produce.
 * Array uploads pin Kotlin arrays via usePinned + addressOf.
 */
class GlIos : Gl {
    override fun createProgram(vertexSrc: String, fragmentSrc: String): Int =
        mwgl_create_program(vertexSrc, fragmentSrc)
    override fun useProgram(p: Int) = mwgl_use_program(p)
    override fun attribLocation(p: Int, name: String): Int = mwgl_attrib_location(p, name)
    override fun uniformLocation(p: Int, name: String): Int = mwgl_uniform_location(p, name)
    override fun uniformMatrix4fv(loc: Int, count: Int, transpose: Boolean, value: FloatArray) {
        value.usePinned { mwgl_uniform_matrix4fv(loc, count, if (transpose) 1 else 0, it.addressOf(0)) }
    }
    override fun uniform1i(loc: Int, v: Int) = mwgl_uniform1i(loc, v)
    override fun uniform1f(loc: Int, v: Float) = mwgl_uniform1f(loc, v)
    override fun uniform3fv(loc: Int, v: FloatArray) { v.usePinned { mwgl_uniform3fv(loc, it.addressOf(0)) } }
    override fun uniform4fv(loc: Int, v: FloatArray) { v.usePinned { mwgl_uniform4fv(loc, it.addressOf(0)) } }

    override fun createBuffer(): Int = mwgl_create_buffer()
    override fun bufferData(target: Int, data: FloatArray, usage: Int) {
        data.usePinned { mwgl_buffer_data(target, it.addressOf(0), data.size, usage) }
    }
    override fun bindBuffer(target: Int, b: Int) = mwgl_bind_buffer(target, b)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        mwgl_vertex_attrib_pointer(index, size, type, if (normalized) 1 else 0, stride, offset)
    override fun enableVertexAttribArray(index: Int) = mwgl_enable_vertex_attrib_array(index)
    override fun disableVertexAttribArray(index: Int) = mwgl_disable_vertex_attrib_array(index)

    override fun createTexture(): Int = mwgl_create_texture()
    override fun bindTexture(target: Int, t: Int) = mwgl_bind_texture(target, t)
    override fun activeTexture(unit: Int) = mwgl_active_texture(unit)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: ByteArray?) {
        if (data == null) mwgl_tex_image_2d(target, level, internalFormat, w, h, border, format, type, null)
        else data.usePinned { mwgl_tex_image_2d(target, level, internalFormat, w, h, border, format, type, it.addressOf(0)) }
    }
    override fun texParameteri(target: Int, pname: Int, value: Int) = mwgl_tex_parameteri(target, pname, value)
    override fun generateMipmap(target: Int) = mwgl_generate_mipmap(target)
    override fun texSubImage2D(target: Int, level: Int, x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, data: ByteArray?) {
        if (data != null) data.usePinned {
            mwgl_tex_image_2d(target, level, format, w, h, 0, format, type, it.addressOf(0))
        }
    }

    override fun createFramebuffer(): Int = mwgl_create_framebuffer()
    override fun bindFramebuffer(target: Int, f: Int) = mwgl_bind_framebuffer(target, f)
    override fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, t: Int, level: Int) =
        mwgl_framebuffer_texture_2d(target, attachment, texTarget, t, level)
    override fun checkFramebufferStatus(target: Int): Int = mwgl_check_framebuffer_status(target)

    override fun viewport(x: Int, y: Int, w: Int, h: Int) = mwgl_viewport(x, y, w, h)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = mwgl_clear_color(r, g, b, a)
    override fun clear(mask: Int) = mwgl_clear(mask)
    override fun enable(cap: Int) = mwgl_enable(cap)
    override fun disable(cap: Int) = mwgl_disable(cap)
    override fun blendFunc(sfactor: Int, dfactor: Int) = mwgl_blend_func(sfactor, dfactor)
    override fun drawArrays(mode: Int, first: Int, count: Int) = mwgl_draw_arrays(mode, first, count)
    override fun getString(name: Int): String = mwgl_get_string(name)?.toKString() ?: "(null)"
}
