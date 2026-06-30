#version 330

uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float r = dot(c, vec3(0.393, 0.769, 0.189));
    float g = dot(c, vec3(0.349, 0.686, 0.168));
    float b = dot(c, vec3(0.272, 0.534, 0.131));
    fragColor = vec4(r, g, b, 1.0);
}
