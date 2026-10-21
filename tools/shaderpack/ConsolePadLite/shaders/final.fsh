#version 120

// ConsolePad Lite: mas saturacion, un poco de contraste y, si se quiere, un tono calido o frio. Las coordenadas salen de la posicion del pixel en la VENTANA
// (gl_FragCoord por el inverso de su tamano), no de las coordenadas de textura del cuadrado: con la pantalla dividida el cuadrado solo cubre la region de un jugador,
// y asi cada region lee sus propios pixeles.
//
// Coste por pixel: una lectura de textura, un producto escalar y una sola multiplicacion y suma. Saturacion y contraste se funden en una sola operacion:
//   ((c - l) * S + l - 0.5) * K + 0.5  =  c * (S * K) + l * (K - S * K) + 0.5 * (1 - K)     con l = luminancia
// y no hace falta limitar el resultado: el buffer de salida ya lo hace al escribir.

#define SATURATION 1.25 //[1.00 1.10 1.25 1.40 1.60]
#define CONTRAST 1.08 //[1.00 1.04 1.08 1.14 1.20]
#define WARMTH 0.00 //[-0.06 -0.03 0.00 0.03 0.06]

uniform sampler2D colortex0;
varying vec2 invView;

void main() {
    vec3 c = texture2D(colortex0, gl_FragCoord.xy * invView).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    const float sk = SATURATION * CONTRAST;
    c = c * sk + vec3(l * (CONTRAST - sk) + 0.5 * (1.0 - CONTRAST));
    c *= vec3(1.0 + WARMTH, 1.0, 1.0 - WARMTH);
    gl_FragColor = vec4(c, 1.0);
}
