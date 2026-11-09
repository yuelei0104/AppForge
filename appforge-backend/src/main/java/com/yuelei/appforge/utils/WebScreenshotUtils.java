package com.yuelei.appforge.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Slf4j
public class WebScreenshotUtils {

    private static final WebDriver webDriver;

    private static final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-proxy-server");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments(
                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                            + " (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    private static void compressImage(String originalImagePath, String compressedImagePath) {
        final float compressionQuality = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    compressionQuality);
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    private static void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(
                    webDriver ->
                            "complete"
                                    .equals(
                                            ((JavascriptExecutor) webDriver)
                                                    .executeScript("return document.readyState")));
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * 截图前先确认页面真实可访问，避免把 Chrome 的 ERR_CONNECTION_REFUSED 错误页当成封面。
     */
    private static boolean isPageReachable(String webUrl) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(webUrl))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
            if (!ok) {
                log.warn("网页不可访问，跳过截图: {}, status={}", webUrl, response.statusCode());
            }
            return ok;
        } catch (Exception e) {
            log.warn("网页不可访问，跳过截图: {}, error={}", webUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Chrome 是否显示了“无法访问此网站”等错误页。
     */
    private static boolean isErrorPage(WebDriver driver) {
        try {
            String title = driver.getTitle();
            String source = driver.getPageSource();
            String content =
                    ((title == null ? "" : title) + "\n" + (source == null ? "" : source))
                            .toLowerCase(Locale.ROOT);
            return content.contains("err_connection")
                    || content.contains("err_name")
                    || content.contains("err_timed_out")
                    || content.contains("this site can't be reached")
                    || content.contains("this site can’t be reached")
                    || content.contains("无法访问此网站")
                    || content.contains("找不到 localhost")
                    || content.contains("拒绝连接")
                    || content.contains("connection refused");
        } catch (Exception e) {
            log.warn("检查页面错误页失败", e);
            return false;
        }
    }

    /**
     * 生成网页截图。
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public static synchronized String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        // 先做一次 HTTP 探测。部署刚完成或服务重启时，直接截图会把错误页存成封面。
        if (!isPageReachable(webUrl)) {
            return null;
        }
        try {
            String rootPath =
                    System.getProperty("user.dir")
                            + File.separator
                            + "tmp"
                            + File.separator
                            + "screenshots"
                            + File.separator
                            + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            final String imageSuffix = ".png";
            String imageSavePath =
                    rootPath + File.separator + RandomUtil.randomNumbers(5) + imageSuffix;

            webDriver.get(webUrl);
            if (isErrorPage(webDriver)) {
                log.warn("页面加载结果为错误页，跳过截图: {}", webUrl);
                return null;
            }
            waitForPageLoad(webDriver);
            if (isErrorPage(webDriver)) {
                log.warn("页面等待后仍为错误页，跳过截图: {}", webUrl);
                return null;
            }

            byte[] screenshotBytes =
                    ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);

            final String compressionSuffix = "_compressed.jpg";
            String compressedImagePath =
                    rootPath + File.separator + RandomUtil.randomNumbers(5) + compressionSuffix;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }
}
