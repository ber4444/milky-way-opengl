#pragma once
#include <OpenGLES/ES2/gl.h>

#ifdef __cplusplus
extern "C" {
#endif

int mwgl_create_program(const char* vert_src, const char* frag_src);
void mwgl_use_program(int program);
int mwgl_attrib_location(int program, const char* name);
int mwgl_uniform_location(int program, const char* name);
void mwgl_uniform_matrix4fv(int loc, int count, int transpose, const float* value);
void mwgl_uniform1i(int loc, int v);
void mwgl_uniform1f(int loc, float v);
void mwgl_uniform3fv(int loc, const float* v);
void mwgl_uniform4fv(int loc, const float* v);
int mwgl_create_buffer(void);
void mwgl_buffer_data(int target, const float* data, int count, int usage);
void mwgl_bind_buffer(int target, int buffer);
void mwgl_vertex_attrib_pointer(int index, int size, int type, int normalized, int stride, int offset);
void mwgl_vertex_attrib_pointer_floats(int index, int size, const float* data, int stride, int offset);
void mwgl_enable_vertex_attrib_array(int index);
void mwgl_disable_vertex_attrib_array(int index);
int mwgl_create_texture(void);
void mwgl_bind_texture(int target, int texture);
void mwgl_active_texture(int unit);
void mwgl_tex_image_2d(int target, int level, int internalFormat, int w, int h, int border, int format, int type, const void* data);
void mwgl_tex_parameteri(int target, int pname, int value);
void mwgl_generate_mipmap(int target);
int mwgl_create_framebuffer(void);
void mwgl_bind_framebuffer(int target, int framebuffer);
void mwgl_framebuffer_texture_2d(int target, int attachment, int textarget, int texture, int level);
int mwgl_check_framebuffer_status(int target);
void mwgl_viewport(int x, int y, int w, int h);
void mwgl_clear_color(float r, float g, float b, float a);
void mwgl_clear(int mask);
void mwgl_enable(int cap);
void mwgl_disable(int cap);
void mwgl_blend_func(int sfactor, int dfactor);
void mwgl_draw_arrays(int mode, int first, int count);
const char* mwgl_get_string(int name);

#ifdef __cplusplus
}
#endif
