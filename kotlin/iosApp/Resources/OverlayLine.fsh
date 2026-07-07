// OverlayLine.fsh — flat color for overlay curves. premultiplied-ready:
// the RGB * a output composites correctly with (ONE, ONE_MINUS_SRC_ALPHA).
precision mediump float;
uniform vec4 Color; // (r,g,b,a) straight; we premultiply here.
void main(){
	gl_FragColor = vec4(Color.rgb * Color.a, Color.a);
}
