package com.yuelei.appforge.cover;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import javax.imageio.*;

@Component
public class CoverRenderer {
    /** An attempt owns its browser; cancellation may only stop that browser's process. */
    public static final class Session implements AutoCloseable {
        private ChromeDriverService service;
        private ChromeDriver driver;
        private Path profile;
        private boolean closed;

        synchronized void service(ChromeDriverService value) {
            if (closed) {
                value.stop();
                throw new IllegalStateException("截图已取消");
            }
            service = value;
        }

        synchronized void driver(ChromeDriver value) {
            if (closed) {
                value.quit();
                throw new IllegalStateException("截图已取消");
            }
            driver = value;
            Object chrome = value.getCapabilities().getCapability("chrome");
            if (chrome instanceof Map<?, ?> info && info.get("userDataDir") instanceof String raw) {
                Path path = Path.of(raw).toAbsolutePath().normalize();
                if (path.startsWith(
                                Path.of(System.getProperty("java.io.tmpdir"))
                                        .toAbsolutePath()
                                        .normalize())
                        && path.getFileName()
                                .toString()
                                .matches(
                                        "(?:scoped_dir[0-9_]+|\\.?org\\.chromium\\.Chromium\\.[A-Za-z0-9]+)"))
                    profile = path;
            }
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            if (driver != null) {
                Object pid = driver.getCapabilities().getCapability("goog:processID");
                if (pid instanceof Number n)
                    ProcessHandle.of(n.longValue())
                            .ifPresent(
                                    p -> {
                                        p.descendants().forEach(ProcessHandle::destroyForcibly);
                                        p.destroyForcibly();
                                    });
            }
            if (service != null) service.stop();
            if (driver != null)
                try {
                    driver.quit();
                } catch (RuntimeException ignored) {
                }
            if (profile != null) AppCoverWorker.removeDirectory(profile);
        }
    }

    public Path render(
            URI uri, String marker, Path directory, CoverProperties config, Session session)
            throws Exception {
        var client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var response =
                client.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200
                || !marker.equals(response.headers().firstValue("X-AppForge-Cover").orElse("")))
            throw new IllegalStateException("截图页面授权或版本校验失败");
        if (config.getDriverPath() == null || config.getDriverPath().isBlank())
            WebDriverManager.chromedriver().timeout(20).setup();
        var builder = new ChromeDriverService.Builder().usingAnyFreePort();
        if (config.getDriverPath() != null && !config.getDriverPath().isBlank())
            builder.usingDriverExecutable(Path.of(config.getDriverPath()).toFile());
        var service = builder.build();
        session.service(service);
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-extensions",
                "--window-size=1600,900",
                "--no-proxy-server");
        options.addArguments(config.getChromeArguments());
        if (config.getChromeBinary() != null && !config.getChromeBinary().isBlank())
            options.setBinary(config.getChromeBinary());
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        ChromeDriver driver =
                new ChromeDriver(
                        service,
                        options,
                        ClientConfig.defaultConfig()
                                .connectionTimeout(Duration.ofSeconds(10))
                                .readTimeout(Duration.ofSeconds(35)));
        session.driver(driver);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
        driver.executeCdpCommand(
                "Emulation.setDeviceMetricsOverride",
                Map.of("width", 1600, "height", 900, "deviceScaleFactor", 1, "mobile", false));
        driver.get(uri.toString());
        if (!marker.equals(
                driver.findElement(By.cssSelector("meta[name='appforge-cover']"))
                        .getAttribute("content"))) throw new IllegalStateException("截图页面发生跳转");
        driver.switchTo().frame(driver.findElement(By.id("cover-content")));
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(
                        d ->
                                Boolean.TRUE.equals(
                                        ((JavascriptExecutor) d)
                                                .executeScript(
                                                        "return document.readyState !== 'loading'"
                                                            + " && !!document.body &&"
                                                            + " document.body.children.length > 0"
                                                            + " && (!document.querySelector('#app')"
                                                            + " || document.querySelector('#app').children.length"
                                                            + " > 0)")));
        driver.executeAsyncScript(
                "const done=arguments[arguments.length-1];const"
                    + " ready=Promise.all([document.fonts?document.fonts.ready:Promise.resolve(),...Array.from(document.images).map(i=>i.complete?Promise.resolve():new"
                    + " Promise(r=>{i.onload=r;i.onerror=r}))]);Promise.race([ready,new"
                    + " Promise(r=>setTimeout(r,2500))]).then(()=>requestAnimationFrame(()=>requestAnimationFrame(done)));");
        driver.switchTo().defaultContent();
        var image =
                ImageIO.read(new ByteArrayInputStream(driver.getScreenshotAs(OutputType.BYTES)));
        if (image == null || image.getWidth() != 1600 || image.getHeight() != 900)
            throw new IllegalStateException("截图尺寸或格式无效");
        Path output = directory.resolve("cover.jpg");
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(stream);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(.75f);
            var rgb =
                    new java.awt.image.BufferedImage(
                            1600, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g = rgb.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            writer.write(null, new IIOImage(rgb, null, null), parameters);
        } finally {
            writer.dispose();
        }
        if (Files.size(output) == 0 || Files.size(output) > 1_048_576)
            throw new IllegalStateException("封面大小无效");
        return output;
    }
}
