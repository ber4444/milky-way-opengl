// SunMarker.vsh — a single billboarded point for the Sun annotation sprite.
// Drawn in screen space (constant gl_PointSize) so it stays legible at all zooms.
attribute vec3 Position;

uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
uniform float pointSize;

void main(){
	vec4 mv = modelViewMatrix * vec4(Position, 1.0);
	gl_Position = projectionMatrix * mv;
	gl_PointSize = pointSize; // fixed screen-space size, independent of distance
}
