package com.yuelei.appforge.workspace;

import cn.hutool.core.util.StrUtil;

import com.mybatisflex.core.paginate.Page;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.dto.app.AppQueryRequest;
import com.yuelei.appforge.model.entity.App;

import jakarta.annotation.Resource;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;

import java.util.*;

/** 使用绑定参数完成名称/描述/标签搜索，公开列表强制过滤可见范围。 */
@Service
public class AppCatalogSearch {
    @Resource private JdbcTemplate jdbcTemplate;

    public Page<App> search(
            AppQueryRequest query, Long owner, boolean publicOnly, boolean featuredOnly) {
        ThrowUtils.throwIf(query == null, ErrorCode.PARAMS_ERROR, "查询参数不能为空");
        int page = query.getPageNum(), size = query.getPageSize();
        ThrowUtils.throwIf(
                page < 1 || size < 1 || size > (publicOnly || owner != null ? 20 : 100),
                ErrorCode.PARAMS_ERROR,
                "分页参数无效");
        ThrowUtils.throwIf(
                publicOnly && page > 50, ErrorCode.PARAMS_ERROR, "公开列表最多浏览 50 页，请缩小搜索范围");
        StringBuilder where = new StringBuilder(" WHERE isDelete=0");
        List<Object> args = new ArrayList<>();
        if (owner != null) {
            where.append(" AND userId=?");
            args.add(owner);
        }
        if (publicOnly) where.append(" AND visibility='PUBLIC'");
        if (featuredOnly) where.append(" AND priority>=99");
        String text =
                StrUtil.trimToEmpty(
                        StrUtil.isNotBlank(query.getSearchText())
                                ? query.getSearchText()
                                : query.getAppName());
        ThrowUtils.throwIf(text.length() > 100, ErrorCode.PARAMS_ERROR, "搜索词最多 100 字");
        if (!text.isBlank()) {
            where.append(
                    " AND (appName LIKE ? ESCAPE '!' OR description LIKE ? ESCAPE '!' OR tags LIKE"
                            + " ? ESCAPE '!')");
            String pattern =
                    "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (StrUtil.isNotBlank(query.getTag())) {
            String tag = AppPolicy.encodeTags(List.of(query.getTag()));
            where.append(" AND tags LIKE ?");
            args.add("%" + tag + "%");
        }
        if (!publicOnly && owner == null) {
            filter(where, args, "id", query.getId());
            filter(where, args, "userId", query.getUserId());
            filter(where, args, "priority", query.getPriority());
            filter(where, args, "visibility", query.getVisibility());
            filter(where, args, "featureStatus", query.getFeatureStatus());
            filter(where, args, "generationStatus", query.getGenerationStatus());
            contains(where, args, "cover", query.getCover());
            contains(where, args, "initPrompt", query.getInitPrompt());
            filter(where, args, "codeGenType", query.getCodeGenType());
            filter(where, args, "deployKey", query.getDeployKey());
        }
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app" + where, Long.class, args.toArray());
        long total = count == null ? 0 : count;
        if (publicOnly) total = Math.min(total, 50L * size);
        String order =
                featuredOnly ? "priority DESC,createTime DESC,id DESC" : "createTime DESC,id DESC";
        List<Object> paged = new ArrayList<>(args);
        paged.add(size);
        paged.add((long) (page - 1) * size);
        List<App> list =
                jdbcTemplate.query(
                        "SELECT * FROM app" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                        BeanPropertyRowMapper.newInstance(App.class),
                        paged.toArray());
        Page<App> result = new Page<>(page, size, total);
        result.setRecords(list);
        return result;
    }

    private void contains(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND ").append(column).append(" LIKE ? ESCAPE '!'");
        args.add("%" + value.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    private void filter(StringBuilder sql, List<Object> args, String column, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) return;
        sql.append(" AND ").append(column).append("=?");
        args.add(value);
    }
}
