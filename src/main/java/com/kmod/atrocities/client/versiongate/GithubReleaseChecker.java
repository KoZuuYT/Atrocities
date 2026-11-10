package com.kmod.atrocities.client.versiongate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.versiongate.ModVersion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;

/** Consulta a ultima Release publicada no GitHub (API publica, sem autenticacao). */
public final class GithubReleaseChecker {

    private static volatile UpdateInfo newerRelease;

    private GithubReleaseChecker() {
    }

    /** Release mais nova que a instalada, se a consulta ja terminou e encontrou uma. */
    public static Optional<UpdateInfo> newerRelease() {
        return Optional.ofNullable(newerRelease);
    }

    public static CompletableFuture<Void> checkAsync(String repo) {
        return CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Atrocities-UpdateChecker")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    Atrocities.LOGGER.info("[VersionGate] GitHub respondeu {} ao procurar atualizacoes.", response.statusCode());
                    return;
                }
                parse(JsonParser.parseString(response.body()).getAsJsonObject(), repo);
            } catch (Exception e) {
                Atrocities.LOGGER.info("[VersionGate] Nao foi possivel procurar atualizacoes: {}", e.toString());
            }
        }, Util.backgroundExecutor());
    }

    private static void parse(JsonObject release, String repo) {
        String tag = release.get("tag_name").getAsString();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        String current = ModVersion.current();
        if (ModVersion.compare(version, current) <= 0) {
            Atrocities.LOGGER.info("[VersionGate] Atrocities {} esta atualizado.", current);
            return;
        }
        String expectedAsset = Atrocities.MODID + "-" + version + ".jar";
        String jarUrl = ModVersion.jarUrl(repo, version);
        for (JsonElement asset : release.getAsJsonArray("assets")) {
            JsonObject obj = asset.getAsJsonObject();
            if (expectedAsset.equals(obj.get("name").getAsString())) {
                jarUrl = obj.get("browser_download_url").getAsString();
            }
        }
        String pageUrl = release.has("html_url") ? release.get("html_url").getAsString() : ModVersion.releasePageUrl(repo, version);
        newerRelease = new UpdateInfo(version, jarUrl, pageUrl, UpdateInfo.Reason.NEWER_RELEASE);
        Atrocities.LOGGER.info("[VersionGate] Nova versao do Atrocities disponivel: {} (instalada: {}).", version, current);
    }
}
