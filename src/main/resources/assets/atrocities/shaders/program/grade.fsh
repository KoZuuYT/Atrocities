#version 150

// Composicao final do Atrocities: distorcao da onda de choque, aberracao cromatica radial, profundidade de campo
// pela profundidade real, bloom, gradacao de cor, vinheta colorida, clarao e granulado de filme.
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;

uniform vec2 OutSize;
uniform float Time;
uniform float Bloom;
uniform float Aberration;
uniform float Vignette;
uniform float Saturation;
uniform float Contrast;
uniform float TintR;
uniform float TintG;
uniform float TintB;
uniform float TintAmount;
uniform float EdgeR;
uniform float EdgeG;
uniform float EdgeB;
uniform float EdgeAmount;
uniform float FlashR;
uniform float FlashG;
uniform float FlashB;
uniform float Flash;
uniform float Dof;
uniform float FocusDepth;
uniform float Near;
uniform float Far;
uniform float ShockX;
uniform float ShockY;
uniform float ShockRadius;
uniform float ShockStrength;
uniform float Grain;

in vec2 texCoord;
out vec4 fragColor;

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * Near * Far) / (Far + Near - z * (Far - Near));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    vec2 aspect = vec2(OutSize.x / OutSize.y, 1.0);

    // Onda de choque: um anel de refracao que cresce a partir do ponto de impacto na tela.
    if (ShockStrength > 0.001) {
        vec2 delta = (uv - vec2(ShockX, ShockY)) * aspect;
        float dist = length(delta);
        float band = dist - ShockRadius;
        float ripple = exp(-band * band / 0.0045) * sign(band + 0.0001);
        uv -= (delta / max(dist, 0.0001)) / aspect * ripple * ShockStrength * 0.03;
    }

    vec2 centered = uv - 0.5;
    float edge = dot(centered, centered);

    // Aberracao cromatica radial: forte nas bordas, quase nada no centro.
    vec2 split = centered * Aberration * (0.012 + edge * 0.06);
    vec3 color;
    color.r = texture(DiffuseSampler, uv + split).r;
    color.g = texture(DiffuseSampler, uv).g;
    color.b = texture(DiffuseSampler, uv - split).b;

    // Profundidade de campo: desfoca o que esta longe do plano de foco (o chefe nas cutscenes).
    if (Dof > 0.001) {
        float depth = texture(DepthSampler, uv).r;
        float distance = depth >= 0.99995 ? Far : linearDepth(depth);
        float coc = clamp(abs(distance - FocusDepth) / (FocusDepth * 0.45 + 2.5), 0.0, 1.0);
        color = mix(color, texture(BlurSampler, uv).rgb, coc * Dof);
    }

    // Bloom: soma suave do brilho borrado.
    color += texture(BloomSampler, uv).rgb * Bloom;

    // Gradacao: saturacao, contraste e um tom de cor de cena.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, Saturation);
    color = (color - 0.5) * Contrast + 0.5;
    vec3 tint = vec3(TintR, TintG, TintB);
    color = mix(color, color * tint * 1.35 + tint * 0.025, TintAmount);

    // Vinheta escura e uma borda colorida (furia, vida baixa).
    float vignette = smoothstep(0.95, 0.25, length(centered * vec2(1.0, 1.15)) * 1.2);
    color *= mix(1.0, vignette, Vignette);
    float rim = smoothstep(0.35, 0.9, length(centered * aspect) * 1.1);
    color = mix(color, vec3(EdgeR, EdgeG, EdgeB), rim * EdgeAmount);

    // Clarao de impacto.
    vec3 flash = vec3(FlashR, FlashG, FlashB);
    color = mix(color, flash, Flash * 0.4) + flash * Flash * 0.15;

    // Granulado de filme nas cenas.
    if (Grain > 0.001) {
        float noise = hash(uv * OutSize + Time * 917.0) - 0.5;
        color += noise * Grain * 0.08;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
