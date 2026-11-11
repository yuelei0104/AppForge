package com.yuelei.appforge.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Keep streaming cancellation independent of transitive Apache client dependencies. */
@Configuration
public class StreamingHttpClientConfig {
    public static HttpClientBuilder builder() {
        return JdkHttpClient.builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofMinutes(2));
    }

    @Bean(name = "openAiStreamingChatModelHttpClientBuilder")
    HttpClientBuilder streamingHttpClientBuilder() {
        return builder();
    }
}
