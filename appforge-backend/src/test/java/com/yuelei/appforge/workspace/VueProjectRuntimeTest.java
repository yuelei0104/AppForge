package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONUtil;
import cn.hutool.setting.yaml.YamlUtil;

import com.yuelei.appforge.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.appforge.core.AiCodeGeneratorFacade;
import com.yuelei.appforge.workspace.vue.*;

import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** 显式启用的外部服务验证，不让普通测试自动调用付费模型或依赖 Docker。 */
class VueProjectRuntimeTest {
    private static final String IMAGE = "node:20.19.0-bookworm-slim";
    private final ProxySelector previousProxy = ProxySelector.getDefault();
    private final Authenticator previousAuthenticator = Authenticator.getDefault();

    @AfterEach
    void restoreNetworkDefaults() {
        ProxySelector.setDefault(previousProxy);
        Authenticator.setDefault(previousAuthenticator);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "APPFORGE_LIVE_MODEL_TEST", matches = "true")
    void realModelWritesProjectThroughNativeTools() throws Exception {
        var config = YamlUtil.loadByPath("application.yml");
        var environment = new StandardEnvironment();
        String prefix = "langchain4j.open-ai.streaming-chat-model.";
        String key =
                setting(
                        "APPFORGE_LIVE_MODEL_API_KEY",
                        config.getByPath(prefix + "api-key"),
                        environment);
        String base =
                setting(
                        "APPFORGE_LIVE_MODEL_BASE_URL",
                        config.getByPath(prefix + "base-url"),
                        environment);
        String model =
                setting(
                        "APPFORGE_LIVE_MODEL_NAME",
                        config.getByPath(prefix + "model-name"),
                        environment);
        assertFalse(key.isBlank(), "真实模型验证需要有效凭证");
        String proxy = Objects.toString(System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"));
        if (proxy != null && !proxy.isBlank()) {
            URI uri = URI.create(proxy);
            ProxySelector.setDefault(
                    ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
            if (uri.getUserInfo() != null) {
                String[] credentials = uri.getUserInfo().split(":", 2);
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
                Authenticator.setDefault(
                        new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return getRequestorType() == RequestorType.PROXY
                                        ? new PasswordAuthentication(
                                                credentials[0],
                                                credentials.length > 1
                                                        ? credentials[1].toCharArray()
                                                        : new char[0])
                                        : null;
                            }
                        });
            }
        }
        var client =
                OpenAiStreamingChatModel.builder()
                        .apiKey(key)
                        .baseUrl(base)
                        .modelName(model)
                        .maxTokens(8192)
                        .timeout(Duration.ofMinutes(2))
                        .logRequests(false)
                        .logResponses(false)
                        .httpClientBuilder(SpringRestClient.builder())
                        .build();
        var captured = new AtomicReference<Map<String, String>>();
        var builder =
                new VueProjectBuilder() {
                    @Override
                    public Map<String, String> build(ProjectTask task) {
                        captured.set(task.seal());
                        throw new ModelStageVerified();
                    }
                };
        var runner = new VueProjectRunner();
        ReflectionTestUtils.setField(runner, "factory", new AiCodeGeneratorServiceFactory());
        ReflectionTestUtils.setField(
                runner,
                "facade",
                new AiCodeGeneratorFacade() {
                    @Override
                    public String contextForProject(String request, long appId, long messageId) {
                        return request;
                    }
                });
        ReflectionTestUtils.setField(runner, "streamingChatModel", client);
        ReflectionTestUtils.setField(runner, "builder", builder);
        var progress = new CopyOnWriteArrayList<GenerationProgress>();
        try (var task =
                new ProjectTask(
                        Map.of(),
                        event -> {
                            progress.add(event);
                            if (event.state().equals("SUCCEEDED"))
                                System.out.println(
                                        "LIVE_TOOL " + event.operation() + " " + event.path());
                        })) {
            assertThrows(
                    ModelStageVerified.class,
                    () ->
                            runner.stream(
                                            1,
                                            1,
                                            "创建中文城市阅读馆网站，包含丰富首页、可搜索书目、到馆指南三个页面和统一导航。"
                                                    + "用原生 CSS 支持手机和平板，使用真实模拟书目及 picsum 图片。"
                                                    + "严格使用目录工具给出的工程配置，先读目录，逐个写文件。控制在 12 个源码文件以内。",
                                            task)
                                    .blockLast(Duration.ofMinutes(7)));
        }
        var files = captured.get();
        assertNotNull(files);
        VueProjectFiles.validateSources(files);
        assertTrue(progress.stream().anyMatch(e -> e.operation().equals("read_directory")));
        long writes =
                progress.stream()
                        .filter(
                                e ->
                                        e.operation().equals("write_file")
                                                && e.state().equals("SUCCEEDED"))
                        .count();
        assertEquals(files.size(), writes);
        Path output = Path.of("target/vue-live-source.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, JSONUtil.toJsonPrettyStr(files), StandardCharsets.UTF_8);
        System.out.println(
                "LIVE_MODEL_VERIFIED model="
                        + model
                        + " sourceFiles="
                        + files.size()
                        + " successfulWrites="
                        + writes);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "APPFORGE_DOCKER_TEST", matches = "true")
    void dockerBuildsTheReviewedRealModelProject() throws Exception {
        Map<String, String> source;
        try (var input = getClass().getResourceAsStream("/vue-live-project.json")) {
            assertNotNull(input, "缺少已审查的真实模型源码样本");
            source =
                    JSONUtil.toBean(
                            new String(input.readAllBytes(), StandardCharsets.UTF_8), Map.class);
        }
        VueProjectFiles.validateSources(source);
        var builder = new VueProjectBuilder();
        ReflectionTestUtils.setField(builder, "image", IMAGE);
        try (var task =
                new ProjectTask(
                        Map.of(),
                        event ->
                                System.out.println(
                                        "DOCKER_PHASE "
                                                + event.operation()
                                                + " "
                                                + event.state()))) {
            source.forEach(task::write);
            var compiled = builder.build(task);
            task.complete(compiled);
            assertTrue(compiled.containsKey("dist/index.html"));
            assertTrue(compiled.keySet().stream().anyMatch(p -> p.matches("dist/assets/.*\\.js")));
            assertTrue(compiled.keySet().stream().anyMatch(p -> p.matches("dist/assets/.*\\.css")));
            assertEquals(source, VueProjectFiles.sources(task.result()));
            Path evidence = Path.of("target/vue-runtime-evidence");
            for (var entry : compiled.entrySet()) {
                Path file = evidence.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, entry.getValue());
            }
            Files.writeString(
                    evidence.resolve("result.json"),
                    JSONUtil.toJsonStr(
                            Map.of(
                                    "image",
                                    IMAGE,
                                    "sourceFiles",
                                    source.size(),
                                    "compiledFiles",
                                    compiled.size(),
                                    "dockerBuild",
                                    "passed")));
            System.out.println(
                    "DOCKER_BUILD_VERIFIED sourceFiles="
                            + source.size()
                            + " compiledFiles="
                            + compiled.size());
        }
    }

    private String setting(String name, Object fallback, StandardEnvironment environment) {
        return environment.resolveRequiredPlaceholders(
                Objects.toString(System.getenv(name), Objects.toString(fallback, "")));
    }

    private static class ModelStageVerified extends RuntimeException {}
}
