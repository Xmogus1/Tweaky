#version 330

uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float g = dot(c, vec3(0.3, 0.59, 0.11));
    fragColor = vec4(g, g, g, 1.0);
}
