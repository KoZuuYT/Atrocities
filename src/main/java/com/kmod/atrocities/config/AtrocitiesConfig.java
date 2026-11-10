package com.kmod.atrocities.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuracoes do Atrocities.
 * <p>
 * {@link #COMMON_SPEC} vale para servidor e cliente (balanceamento e VersionGate).
 * {@link #CLIENT_SPEC} so existe no cliente (verificacao de atualizacoes).
 */
public final class AtrocitiesConfig {

    private AtrocitiesConfig() {
    }

    // ------------------------------------------------------------------ COMMON
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();

    /** Teto do atributo minecraft:generic.max_health (vanilla = 1024). */
    public static final ModConfigSpec.DoubleValue MAX_HEALTH_CAP;

    /** Dano por hit a partir do qual o soft cap dos chefes comeca a reduzir. */
    public static final ModConfigSpec.DoubleValue BOSS_SOFT_CAP_THRESHOLD;
    /** Fracao do dano excedente que passa pelo soft cap (0.35 = 35%). */
    public static final ModConfigSpec.DoubleValue BOSS_SOFT_CAP_FACTOR;

    /** Desconecta, com aviso e link de download, quem entra sem o mod. */
    public static final ModConfigSpec.BooleanValue KICK_CLIENTS_WITHOUT_MOD;
    /** Exige que o cliente use exatamente a mesma versao do servidor. */
    public static final ModConfigSpec.BooleanValue REQUIRE_EXACT_VERSION;
    /** Repositorio do GitHub (dono/nome) de onde os jars sao baixados. */
    public static final ModConfigSpec.ConfigValue<String> GITHUB_REPO;

    static {
        COMMON.comment("Atributos globais").push("attributes");
        MAX_HEALTH_CAP = COMMON
                .comment("Valor maximo do atributo max_health. O vanilla limita em 1024, o que impede chefes de 3.000 a 15.000+ HP.")
                .defineInRange("maxHealthCap", 1_000_000.0D, 1024.0D, Double.MAX_VALUE);
        COMMON.pop();

        COMMON.comment("Mitigacao de dano dos chefes (meta de ~50 de dano por hit)").push("bossCombat");
        BOSS_SOFT_CAP_THRESHOLD = COMMON
                .comment("Hits acima deste valor tem o excedente reduzido.")
                .defineInRange("softCapThreshold", 30.0D, 0.0D, 10_000.0D);
        BOSS_SOFT_CAP_FACTOR = COMMON
                .comment("Fracao do excedente que passa. Ex.: limiar 30, fator 0.35 -> hit de 50 vira 37.")
                .defineInRange("softCapFactor", 0.35D, 0.0D, 1.0D);
        COMMON.pop();

        COMMON.comment("VersionGate: controle de versao entre servidor e clientes").push("versionGate");
        KICK_CLIENTS_WITHOUT_MOD = COMMON
                .comment("Se true, jogadores sem o Atrocities sao desconectados com uma mensagem contendo o link de download.")
                .define("kickClientsWithoutMod", true);
        REQUIRE_EXACT_VERSION = COMMON
                .comment("Se true, clientes com versao diferente da do servidor sao desconectados apos o aviso de atualizacao.")
                .define("requireExactVersion", false);
        GITHUB_REPO = COMMON
                .comment("Repositorio do GitHub no formato dono/nome, usado para montar os links de download.")
                .define("githubRepo", "KoZuuYT/Atrocities");
        COMMON.pop();
    }

    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ------------------------------------------------------------------ CLIENT
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    /** Consulta a ultima Release do GitHub ao abrir o jogo. */
    public static final ModConfigSpec.BooleanValue CHECK_FOR_UPDATES;
    /** Mostra o botao de atualizacao no menu principal. */
    public static final ModConfigSpec.BooleanValue TITLE_SCREEN_NOTICE;

    static {
        CLIENT.comment("Atualizacoes").push("updates");
        CHECK_FOR_UPDATES = CLIENT
                .comment("Consulta o GitHub em busca de uma versao nova ao iniciar o jogo.")
                .define("checkForUpdates", true);
        TITLE_SCREEN_NOTICE = CLIENT
                .comment("Mostra um botao no menu principal quando existe uma versao nova.")
                .define("titleScreenNotice", true);
        CLIENT.pop();
    }

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();
}
