#version 150
// Clip behind the destination aperture and fade the bounded scene without exposing an empty void.
uniform sampler2D Sampler0;
uniform vec4 ClipPlane;
uniform vec3 SceneFog;
in vec2 texCoord;
in vec4 tint;
in vec3 localPosition;
out vec4 fragColor;
void main() {
    if (dot(vec4(localPosition, 1.0), ClipPlane) < -0.001) discard;
    vec4 color = texture(Sampler0, texCoord) * tint;
    if (color.a < 0.1) discard;
    float fog = smoothstep(16.0, 28.0, length(localPosition));
    fragColor = vec4(mix(color.rgb, SceneFog, fog), color.a);
}
