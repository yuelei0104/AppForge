package com.yuelei.appforge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.yuelei.appforge.mapper")
public class AppForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppForgeApplication.class, args);
    }
}
