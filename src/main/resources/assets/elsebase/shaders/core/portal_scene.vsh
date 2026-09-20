#version 150
// Destination-local geometry, never the active world's camera or lightmap.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
out vec4 tint;
out vec3 localPosition;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    tint = Color;
    localPosition = Position;
}
