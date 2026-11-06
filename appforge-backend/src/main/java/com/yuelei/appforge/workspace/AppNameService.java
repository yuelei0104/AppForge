package com.yuelei.appforge.workspace;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.AiCodeGeneratorService;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/** 命名独立于创建与代码生成；超时沿用可读默认名称，绝不覆盖用户手动重命名。 */
@Service
@Slf4j
public class AppNameService {
    @Resource private AiCodeGeneratorService ai;
    @Resource private JdbcTemplate jdbcTemplate;

    public static String fallback(String prompt) {
        String text = prompt.replaceFirst("^(请|帮我|请帮我)?(创建|生成|制作|设计)(一个|一份)?", "").trim();
        text = text.split("[，。；,;\\n]", 2)[0];
        return text.isBlank() ? "我的应用" : text.substring(0, Math.min(text.length(), 20));
    }

    public void suggest(long id, String prompt, String original) {
        Mono.fromCallable(() -> ai.generateAppName(prompt))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(8))
                .subscribe(
                        json -> {
                            try {
                                String name = JSONUtil.parseObj(json).getStr("name", "").trim();
                                if (!name.isBlank() && name.length() <= 50)
                                    jdbcTemplate.update(
                                            "UPDATE app SET appName=? WHERE id=? AND appName=? AND"
                                                    + " isDelete=0",
                                            name,
                                            id,
                                            original);
                            } catch (RuntimeException e) {
                                log.debug("AI 命名不可用，保留默认名称 appId={}", id);
                            }
                        },
                        error -> log.debug("AI 命名失败，保留默认名称 appId={}", id));
    }
}
