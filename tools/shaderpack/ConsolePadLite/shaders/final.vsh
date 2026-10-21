#version 120

// ConsolePad Lite: paso final de color sin estado entre fotogramas (ni sombras, ni exposicion automatica, ni TAA). Pensado para la pantalla dividida.
// El inverso del tamano de la ventana se calcula una vez por vertice (4 en total) y no dos divisiones por cada pixel.
uniform float viewWidth;
uniform float viewHeight;
varying vec2 invView;

void main() {
    gl_Position = ftransform();
    invView = vec2(1.0 / viewWidth, 1.0 / viewHeight);
}
