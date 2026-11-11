package com.yuelei.appforge.cover;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

@EnabledIfEnvironmentVariable(named = "APPFORGE_COVER_CHROME_TEST", matches = "true")
class CoverChromeTest extends CoverTestDatabase {
    @TempDir Path directory;

    @Test
    void actualPrivateNativeScreenshotIsJpegAndRejectsBadToken() throws Exception {
        var attempt = claim();
        config.setDriverPath(System.getenv("APPFORGE_COVER_DRIVER_PATH"));
        config.setChromeBinary(System.getenv("APPFORGE_COVER_CHROME_BINARY"));
        extraArguments();
        try (var server = newServer(attempt);
                var session = new CoverRenderer.Session()) {
            URI uri =
                    URI.create(
                            "http://127.0.0.1:"
                                    + server.server.getAddress().getPort()
                                    + "/render?token="
                                    + attempt.renderToken());
            Path image =
                    new CoverRenderer()
                            .render(
                                    uri,
                                    attempt.id() + ":" + attempt.execution(),
                                    directory,
                                    config,
                                    session);
            var decoded = ImageIO.read(image.toFile());
            assertEquals(1600, decoded.getWidth());
            assertEquals(900, decoded.getHeight());
            assertTrue(Files.size(image) > 1000);
            Files.createDirectories(Path.of("target/cover-evidence"));
            Files.copy(
                    image,
                    Path.of("target/cover-evidence/private-version.jpg"),
                    StandardCopyOption.REPLACE_EXISTING);
            assertThrows(
                    Exception.class,
                    () ->
                            new CoverRenderer()
                                    .render(
                                            URI.create(
                                                    uri.toString()
                                                            .replace(
                                                                    attempt.renderToken(),
                                                                    "invalid")),
                                            "bad",
                                            directory,
                                            config,
                                            new CoverRenderer.Session()));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "APPFORGE_COVER_PROJECT_DIST", matches = ".+")
    void actualBuiltVueProjectMountsAndScreenshots() throws Exception {
        Path dist = Path.of(System.getenv("APPFORGE_COVER_PROJECT_DIST"));
        Map<String, String> files = new LinkedHashMap<>();
        files.put("package.json", "{}");
        try (var paths = Files.walk(dist)) {
            for (Path p : paths.filter(Files::isRegularFile).toList())
                files.put(
                        "dist/" + dist.relativize(p).toString().replace('\\', '/'),
                        Files.readString(p));
        }
        jdbc.update("DELETE FROM app_version");
        files(1, 1, files);
        var attempt = claim();
        config.setDriverPath(System.getenv("APPFORGE_COVER_DRIVER_PATH"));
        config.setChromeBinary(System.getenv("APPFORGE_COVER_CHROME_BINARY"));
        extraArguments();
        try (var server = newServer(attempt);
                var session = new CoverRenderer.Session()) {
            Path image =
                    new CoverRenderer()
                            .render(
                                    URI.create(
                                            "http://127.0.0.1:"
                                                    + server.server.getAddress().getPort()
                                                    + "/render?token="
                                                    + attempt.renderToken()),
                                    attempt.id() + ":" + attempt.execution(),
                                    directory,
                                    config,
                                    session);
            assertTrue(Files.size(image) > 12000);
            Files.createDirectories(Path.of("target/cover-evidence"));
            Files.copy(
                    image,
                    Path.of("target/cover-evidence/private-project.jpg"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void extraArguments() {
        if ("true".equals(System.getenv("APPFORGE_COVER_CHROME_SINGLE_PROCESS")))
            config.setChromeArguments(
                    List.of(
                            "--single-process",
                            "--no-zygote",
                            "--in-process-gpu",
                            "--use-gl=angle",
                            "--use-angle=swiftshader",
                            "--enable-unsafe-swiftshader"));
    }

    Server newServer(AppCoverTasks.Attempt attempt) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/render",
                exchange -> {
                    try {
                        String token =
                                exchange.getRequestURI().getQuery().replaceFirst("^token=", "");
                        var result = tasks.render(attempt.id(), token);
                        exchange.getResponseHeaders()
                                .set("Content-Type", "text/html; charset=utf-8");
                        exchange.getResponseHeaders().set("Content-Security-Policy", result.csp());
                        exchange.getResponseHeaders().set("X-AppForge-Cover", result.marker());
                        byte[] bytes = result.html().getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (Exception e) {
                        exchange.sendResponseHeaders(404, -1);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        return new Server(server);
    }

    record Server(HttpServer server) implements AutoCloseable {
        public void close() {
            server.stop(0);
        }
    }
}
