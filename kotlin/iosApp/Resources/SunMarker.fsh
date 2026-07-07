// SunMarker.fsh — sample the marker sprite via gl_PointCoord (point-sprite UVs).
precision mediump float;
uniform sampler2D Sampler;
void main(){
	vec4 c = texture2D(Sampler, gl_PointCoord);
	if (c.a < 0.01) discard;          // crisp edges, no depth interaction
	gl_FragColor = c;                 // already premultiplied at upload
}
