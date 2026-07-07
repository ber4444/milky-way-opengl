// OverlayLine.vsh — minimal vertex transform for overlay curves (spirals, rings).
// Drawn in world/model space like everything else, so they sit correctly
// against the galaxy as the camera rotates. Uses LINE_STRIP / LINE_LOOP.
attribute vec3 Position;

uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

void main(){
	gl_Position = projectionMatrix * (modelViewMatrix * vec4(Position, 1.0));
}
