#version 330

uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    fragColor = vec4(c.g, c.b, c.r, 1.0);
}
