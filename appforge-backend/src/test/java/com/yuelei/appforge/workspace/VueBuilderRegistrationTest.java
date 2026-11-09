package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.yuelei.appforge.workspace.vue.VueProjectBuilder;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class VueBuilderRegistrationTest {
    @Test
    void componentScanningRegistersOnlyTheDockerBuilder() {
        try (var context = new GenericApplicationContext()) {
            var scanner = new ClassPathBeanDefinitionScanner(context);
            var builders =
                    Set.of(
                            "com.yuelei.appforge.core.builder.VueProjectBuilder",
                            "com.yuelei.appforge.workspace.vue.VueProjectBuilder");
            scanner.addExcludeFilter(
                    (metadata, factory) ->
                            !builders.contains(metadata.getClassMetadata().getClassName()));
            scanner.scan("com.yuelei.appforge.core.builder", "com.yuelei.appforge.workspace.vue");
            context.refresh();
            assertInstanceOf(VueProjectBuilder.class, context.getBean("vueProjectBuilder"));
            assertEquals(1, context.getBeansOfType(VueProjectBuilder.class).size());
            assertTrue(
                    context.getBeansOfType(com.yuelei.appforge.core.builder.VueProjectBuilder.class)
                            .isEmpty());
        }
    }
}
