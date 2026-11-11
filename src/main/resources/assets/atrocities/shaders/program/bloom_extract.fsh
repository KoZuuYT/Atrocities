#version 150

// Extrai so o que brilha (mascaras emissivas, energia, fogo) para o bloom. O ceu fica de fora.
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    float peak = max(color.r, max(color.g, color.b));
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    // Cores saturadas e vivas contam mais do que branco chapado (neve, nuvem).
    float saturation = peak - min(color.r, min(color.g, color.b));
    float weight = smoothstep(Threshold, 1.0, peak) * (0.45 + 0.55 * saturation) + smoothstep(0.93, 1.0, luma) * 0.25;
    float sky = step(0.99995, depth);
    fragColor = vec4(color * weight * (1.0 - sky), 1.0);
}
