package com.yuelei.appforge.generator;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

/**
 * 根据数据库表生成基础代码。
 */
public class MyBatisCodeGenerator {

    // 只生成 user 表对应的代码
    private static final String[] TABLE_NAMES = {"user"};

    public static void main(String[] args) {
        // 读取 application.yml 中的数据源配置
        Dict dict = YamlUtil.loadByPath("application.yml");
        Map<String, Object> dataSourceConfig =
            dict.getByPath("spring.datasource");

        if (dataSourceConfig == null) {
            throw new IllegalStateException(
                "application.yml 中未找到 spring.datasource 配置"
            );
        }

        String url = Convert.toStr(dataSourceConfig.get("url"));
        String username = Convert.toStr(dataSourceConfig.get("username"));
        String password = Convert.toStr(dataSourceConfig.get("password"));

        // 生成结束后自动关闭连接池
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);

            GlobalConfig globalConfig = createGlobalConfig();
            Generator generator = new Generator(dataSource, globalConfig);

            generator.generate();

            System.out.println("代码生成完成，请查看 target/codegen 目录");
        }
    }

    public static GlobalConfig createGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();

        // 先输出到临时目录，检查后再移入项目源码目录
        globalConfig.getPackageConfig()
            .setSourceDir("target/codegen/java")
            .setBasePackage("com.yuelei.appforge.genresult")
            .setMapperXmlPath("target/codegen/resources/mapper");

        // 指定生成的表，以及逻辑删除字段
        globalConfig.getStrategyConfig()
            .setGenerateTable(TABLE_NAMES)
            .setLogicDeleteColumn("isDelete");

        // 实体类
        globalConfig.enableEntity()
            .setWithLombok(true)
            .setJdkVersion(21);

        // Mapper 与 XML
        globalConfig.enableMapper()
            .setMapperAnnotation(true);
        globalConfig.enableMapperXml();

        // Service 与实现类
        globalConfig.enableService();
        globalConfig.enableServiceImpl();

        // Controller
        globalConfig.enableController();

        // 生成文件的作者信息
        globalConfig.getJavadocConfig()
            .setAuthor("yuelei")
            .setSince("");

        return globalConfig;
    }
}
