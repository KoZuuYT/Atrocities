package com.kmod.atrocities.client.versiongate;

import com.kmod.atrocities.versiongate.ModVersion;

/**
 * Uma versao do Atrocities que o jogador pode instalar.
 *
 * @param version  versao alvo (ex.: "0.2.0")
 * @param jarUrl   link direto do .jar na Release do GitHub
 * @param pageUrl  pagina da Release
 * @param reason   por que a atualizacao esta sendo oferecida
 */
public record UpdateInfo(String version, String jarUrl, String pageUrl, Reason reason) {

    public enum Reason {
        /** O servidor usa outra versao (aceita, mas recomenda trocar). */
        SERVER_MISMATCH,
        /** O servidor exige exatamente a versao dele: o jogador foi desconectado. */
        SERVER_REQUIRED,
        /** Existe uma Release mais nova no GitHub. */
        NEWER_RELEASE
    }

    public static UpdateInfo fromServer(String version, String repo, Reason reason) {
        return new UpdateInfo(version, ModVersion.jarUrl(repo, version), ModVersion.releasePageUrl(repo, version), reason);
    }
}
