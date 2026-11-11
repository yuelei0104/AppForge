package com.yuelei.appforge.constant;

import java.io.File;

public interface AppConstant {

    /**
     * 项目根目录（兼容从项目根目录或 appforge-backend 目录启动）
     */
    String PROJECT_ROOT_DIR = resolveProjectRoot();

    /**
     * 默认应用名称
     */
    String DEFAULT_APP_NAME = "未命名应用";

    /**
     * 用户应用列表每页最大数量
     */
    int USER_PAGE_SIZE_LIMIT = 20;

    /**
     * 应用名称最大长度
     */
    int APP_NAME_MAX_LENGTH = 256;

    /**
     * 应用封面最大长度
     */
    int COVER_MAX_LENGTH = 512;

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = PROJECT_ROOT_DIR + File.separator + "tmp" + File.separator + "code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = PROJECT_ROOT_DIR + File.separator + "tmp" + File.separator + "code_deploy";

    /** 附件本地回退目录；COS 不可用时仍可预览和读取。 */
    String UPLOAD_ROOT_DIR = PROJECT_ROOT_DIR + File.separator + "tmp" + File.separator + "uploads";

    /**
     * 解析项目根目录。
     *
     * <p>IntelliJ 和 Maven 的 working directory 可能不同：
     * 一种是项目根目录，一种是 appforge-backend 模块目录。
     * 统一解析到项目根目录，保证生成目录、部署目录和静态资源目录一致。</p>
     *
     * @return 项目根目录绝对路径
     */
    static String resolveProjectRoot() {
        File currentDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        // 从项目根目录启动
        if (new File(currentDir, "appforge-backend").isDirectory()) {
            return currentDir.getPath();
        }
        // 从 appforge-backend 模块目录启动
        if ("appforge-backend".equals(currentDir.getName()) && currentDir.getParentFile() != null) {
            return currentDir.getParentFile().getPath();
        }
        return currentDir.getPath();
    }
}
