package com.yuelei.appforge.cover;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data @Component @ConfigurationProperties(prefix="app.cover")
public class CoverProperties {
    private boolean enabled;
    private String baseUrl="http://127.0.0.1:8123/api";
    private String publicBaseUrl="http://localhost:8123/api";
    private String tempRoot="tmp/cover-tasks";
    private int queueCapacity=20;
    private int maxAttempts=3;
    private int attemptTimeoutSeconds=90;
    private int leaseSeconds=120;
    private String chromeBinary;
    private String driverPath;
    private java.util.List<String> chromeArguments=java.util.List.of();
    private java.util.List<String> imageHosts=java.util.List.of("images.unsplash.com","images.pexels.com","picsum.photos","fastly.picsum.photos");
}
