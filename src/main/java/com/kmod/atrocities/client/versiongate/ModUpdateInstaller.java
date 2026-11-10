package com.kmod.atrocities.client.versiongate;

import com.kmod.atrocities.Atrocities;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import net.minecraft.Util;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Baixa um jar do Atrocities direto do GitHub e o instala quando o jogo fecha.
 * <p>
 * O jar em uso fica travado pelo Windows enquanto o jogo roda, entao a troca e feita em duas etapas:
 * <ol>
 *   <li>O novo jar e baixado e validado em {@code mods/.atrocities-update/}. A pasta comeca com ponto e
 *       nao e lida pelo NeoForge, entao nunca existem duas versoes do mod carregadas ao mesmo tempo.</li>
 *   <li>Ao fechar o jogo, um processo auxiliar espera o jar antigo ser liberado, apaga-o e move o novo
 *       para {@code mods/}. Se algo falhar, a versao antiga continua intacta.</li>
 * </ol>
 */
public final class ModUpdateInstaller {

    public enum State { IDLE, DOWNLOADING, READY, FAILED }

    private static final ModUpdateInstaller INSTANCE = new ModUpdateInstaller();

    private volatile State state = State.IDLE;
    private volatile float progress;
    private volatile String error = "";
    private volatile String stagedVersion = "";
    private boolean shutdownHookRegistered;

    private ModUpdateInstaller() {
    }

    public static ModUpdateInstaller get() {
        return INSTANCE;
    }

    public State state() {
        return state;
    }

    /** Progresso do download, de 0 a 1 (ou -1 se o servidor nao informou o tamanho). */
    public float progress() {
        return progress;
    }

    public String error() {
        return error;
    }

    public String stagedVersion() {
        return stagedVersion;
    }

    /** Jar do Atrocities em uso, ou vazio em ambiente de desenvolvimento (onde o mod nao e um jar). */
    public static Optional<Path> currentJar() {
        return Optional.ofNullable(ModList.get().getModFileById(Atrocities.MODID))
                .map(info -> info.getFile().getFilePath())
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"));
    }

    public static boolean canSelfUpdate() {
        return currentJar().isPresent();
    }

    public synchronized void start(UpdateInfo info) {
        if (state == State.DOWNLOADING || (state == State.READY && stagedVersion.equals(info.version()))) {
            return;
        }
        Optional<Path> oldJar = currentJar();
        if (oldJar.isEmpty()) {
            fail("Atualizacao automatica indisponivel neste ambiente.");
            return;
        }
        state = State.DOWNLOADING;
        progress = 0F;
        error = "";
        CompletableFuture.runAsync(() -> download(info, oldJar.get()), Util.ioPool());
    }

    private void download(UpdateInfo info, Path oldJar) {
        Path modsDir = FMLPaths.MODSDIR.get();
        Path stagingDir = modsDir.resolve(".atrocities-update");
        String fileName = Atrocities.MODID + "-" + info.version() + ".jar";
        Path part = stagingDir.resolve(fileName + ".part");
        Path staged = stagingDir.resolve(fileName);
        try {
            Files.createDirectories(stagingDir);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(info.jarUrl()))
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "Atrocities-Updater")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                fail("GitHub respondeu HTTP " + response.statusCode() + ".");
                return;
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                long read = 0;
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                    read += n;
                    progress = total > 0 ? (float) read / total : -1F;
                }
            }
            validate(part);
            Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING);
            scheduleApplyOnExit(oldJar, staged, modsDir.resolve(fileName));
            stagedVersion = info.version();
            progress = 1F;
            state = State.READY;
            Atrocities.LOGGER.info("[VersionGate] Atrocities {} baixado; sera instalado ao fechar o jogo.", info.version());
        } catch (Exception e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // O .part sera sobrescrito na proxima tentativa.
            }
            Atrocities.LOGGER.error("[VersionGate] Falha ao baixar a atualizacao", e);
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Garante que o arquivo baixado e mesmo um mod NeoForge (e nao uma pagina de erro). */
    private static void validate(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (zip.getEntry("META-INF/neoforge.mods.toml") == null) {
                throw new IOException("o arquivo baixado nao e um mod NeoForge valido");
            }
        }
    }

    private synchronized void scheduleApplyOnExit(Path oldJar, Path staged, Path target) {
        PendingSwap.set(oldJar, staged, target);
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(PendingSwap::launch, "Atrocities-Updater"));
    }

    private void fail(String message) {
        error = message;
        state = State.FAILED;
    }

    /** Troca pendente; so a ultima baixada e aplicada. */
    private static final class PendingSwap {
        private static Path oldJar;
        private static Path staged;
        private static Path target;

        static synchronized void set(Path oldJar, Path staged, Path target) {
            PendingSwap.oldJar = oldJar.toAbsolutePath();
            PendingSwap.staged = staged.toAbsolutePath();
            PendingSwap.target = target.toAbsolutePath();
        }

        static synchronized void launch() {
            if (oldJar == null) {
                return;
            }
            try {
                ProcessBuilder builder = isWindows() ? windowsSwap() : unixSwap();
                builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException e) {
                Atrocities.LOGGER.error("[VersionGate] Nao foi possivel iniciar o instalador da atualizacao", e);
            }
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        }

        /**
         * PowerShell com -EncodedCommand (UTF-16LE): funciona com caminhos com espacos e acentos,
         * como "C:\Users\Usuario\AppData\...".
         */
        private static ProcessBuilder windowsSwap() {
            String script = "$old=" + psQuote(oldJar) + ";$new=" + psQuote(staged) + ";$dst=" + psQuote(target) + ";"
                    + "for($i=0;$i -lt 120;$i++){try{if($old -ne $dst){Remove-Item -LiteralPath $old -Force -ErrorAction Stop};break}catch{Start-Sleep -Milliseconds 500}};"
                    + "if(($old -eq $dst) -or -not (Test-Path -LiteralPath $old)){Move-Item -LiteralPath $new -Destination $dst -Force}";
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            return new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-EncodedCommand", encoded);
        }

        /** Os caminhos vao como $0/$1/$2, sem interpolacao no texto do script. */
        private static ProcessBuilder unixSwap() {
            String script = "i=0; while [ $i -lt 120 ]; do "
                    + "if [ \"$0\" = \"$2\" ] || rm -f \"$0\"; then break; fi; i=$((i+1)); sleep 0.5; done; "
                    + "if [ \"$0\" = \"$2\" ] || [ ! -e \"$0\" ]; then mv -f \"$1\" \"$2\"; fi";
            return new ProcessBuilder("sh", "-c", script, oldJar.toString(), staged.toString(), target.toString());
        }

        private static String psQuote(Path path) {
            return "'" + path.toString().replace("'", "''") + "'";
        }
    }
}
