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
    /** Multiplicador da vida dos chefes. */
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
    /** Multiplicador do dano dos chefes. */
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_MULTIPLIER;
    /** Anuncia no chat do servidor quando um chefe desperta ou morre. */
    public static final ModConfigSpec.BooleanValue BOSS_BROADCAST;

    /** Entrega o Codex Atrocitas a quem entra no mundo pela primeira vez. */
    public static final ModConfigSpec.BooleanValue GIVE_CODEX_ON_JOIN;

    /** Desconecta, com aviso e link de download, quem entra sem o mod. */
    public static final ModConfigSpec.BooleanValue KICK_CLIENTS_WITHOUT_MOD;
    /** Exige que o cliente use exatamente a mesma versao do servidor. */
    public static final ModConfigSpec.BooleanValue REQUIRE_EXACT_VERSION;
    /** Repositorio do GitHub (dono/nome) de onde os jars sao baixados. */
    public static final ModConfigSpec.ConfigValue<String> GITHUB_REPO;

    static {
        COMMON.comment("Atributos globais").push("attributes");
        MAX_HEALTH_CAP = COMMON
                .comment("Valor maximo do atributo max_health. O vanilla limita em 1024, o que impede chefes de 900 a 3.000 HP.")
                .defineInRange("maxHealthCap", 1_000_000.0D, 1024.0D, Double.MAX_VALUE);
        COMMON.pop();

        COMMON.comment("Mitigacao de dano dos chefes (meta de ~50 de dano por hit)").push("bossCombat");
        BOSS_SOFT_CAP_THRESHOLD = COMMON
                .comment("Hits acima deste valor tem o excedente reduzido.")
                .defineInRange("hitSoftCapThreshold", 40.0D, 0.0D, 10_000.0D);
        BOSS_SOFT_CAP_FACTOR = COMMON
                .comment("Fracao do excedente que passa. Ex.: limiar 40, fator 0.6 -> hit de 50 vira 46.")
                .defineInRange("hitSoftCapFactor", 0.6D, 0.0D, 1.0D);
        BOSS_HEALTH_MULTIPLIER = COMMON
                .comment("Multiplica a vida de todos os chefes (base: Tier I 900, II 1.400, III 2.000, IV 2.700; teto de 3.000 com os tracos).")
                .defineInRange("bossHealthMultiplier", 1.0D, 0.01D, 100.0D);
        BOSS_DAMAGE_MULTIPLIER = COMMON
                .comment("Multiplica o dano de todos os ataques dos chefes.")
                .defineInRange("bossDamageMultiplier", 1.0D, 0.0D, 100.0D);
        BOSS_BROADCAST = COMMON
                .comment("Anuncia para todo o servidor quando um chefe desperta ou e abatido.")
                .define("broadcastBossEvents", true);
        COMMON.pop();

        COMMON.comment("Codex Atrocitas (livro-guia)").push("codex");
        GIVE_CODEX_ON_JOIN = COMMON
                .comment("Entrega o Codex Atrocitas a cada jogador na primeira vez que ele entra no mundo.")
                .define("giveOnFirstJoin", true);
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
