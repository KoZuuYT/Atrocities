package com.kmod.atrocities.versiongate;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.config.AtrocitiesConfig;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Versao do mod em execucao e links do GitHub derivados dela.
 * <p>
 * Convencao de release: tag {@code v<versao>} com o asset {@code atrocities-<versao>.jar}
 * (gerada por {@code release.ps1}).
 */
public final class ModVersion {

    private ModVersion() {
    }

    /** Versao do jar carregado (ex.: "0.1.0"). */
    public static String current() {
        return ModList.get().getModContainerById(Atrocities.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("0.0.0");
    }

    /** Repositorio configurado, no formato dono/nome. */
    public static String repo() {
        return AtrocitiesConfig.GITHUB_REPO.get();
    }

    public static String jarUrl(String repo, String version) {
        return "https://github.com/" + repo + "/releases/download/v" + version + "/" + Atrocities.MODID + "-" + version + ".jar";
    }

    public static String releasePageUrl(String repo, String version) {
        return "https://github.com/" + repo + "/releases/tag/v" + version;
    }

    public static String releasesUrl(String repo) {
        return "https://github.com/" + repo + "/releases";
    }

    /** Compara versoes semanticas: negativo se a &lt; b, zero se iguais, positivo se a &gt; b. */
    public static int compare(String a, String b) {
        return new DefaultArtifactVersion(a).compareTo(new DefaultArtifactVersion(b));
    }
}
