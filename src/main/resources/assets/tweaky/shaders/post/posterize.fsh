#version 330

uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    fragColor = vec4(floor(c * 6.0) / 6.0, 1.0);
}
