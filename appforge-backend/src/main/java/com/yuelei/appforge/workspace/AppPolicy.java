package com.yuelei.appforge.workspace;

import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.User;

import java.util.*;

/** 所有详情、资源和版本接口共用的权限与元数据规则。 */
public final class AppPolicy {
    private AppPolicy() {}

    public static boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getUserRole());
    }

    public static boolean isOwner(App app, User user) {
        return user != null
                && user.getId() != null
                && Objects.equals(app.getUserId(), user.getId());
    }

    public static boolean isPublic(App app) {
        return "PUBLIC".equals(app.getVisibility())
                || (app.getVisibility() == null
                        && app.getPriority() != null
                        && app.getPriority() >= 99);
    }

    public static boolean canRead(App app, User user) {
        return isPublic(app) || isOwner(app, user) || isAdmin(user);
    }

    public static void manage(App app, User user) {
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(
                !isOwner(app, user) && !isAdmin(user), ErrorCode.NO_AUTH_ERROR, "无权操作该应用");
    }

    public static void owner(App app, User user) {
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(!isOwner(app, user), ErrorCode.NO_AUTH_ERROR, "仅创建者可以操作该应用");
    }

    public static String description(String value) {
        String text = value == null ? "" : value.trim();
        ThrowUtils.throwIf(text.length() > 1000, ErrorCode.PARAMS_ERROR, "描述最多 1000 字");
        return text;
    }

    public static String visibility(String value) {
        ThrowUtils.throwIf(
                !Set.of("PUBLIC", "PRIVATE").contains(value),
                ErrorCode.PARAMS_ERROR,
                "可见范围只能为公开或私有");
        return value;
    }

    public static String encodeTags(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String value : values) {
            String tag = value == null ? "" : value.trim();
            ThrowUtils.throwIf(
                    !tag.matches("[\\p{L}\\p{N} +.\\-]{1,20}"),
                    ErrorCode.PARAMS_ERROR,
                    "标签为 1 至 20 字，可使用文字、数字、空格、加号和短横线");
            tags.add(tag);
        }
        ThrowUtils.throwIf(tags.size() > 5, ErrorCode.PARAMS_ERROR, "最多添加 5 个标签");
        return "|" + String.join("|", tags) + "|";
    }

    public static List<String> decodeTags(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\|")).filter(s -> !s.isBlank()).toList();
    }

    public static void idle(App app) {
        ThrowUtils.throwIf(
                "GENERATING".equals(app.getGenerationStatus()),
                ErrorCode.OPERATION_ERROR,
                "请先停止当前生成，再执行此操作");
    }
}
