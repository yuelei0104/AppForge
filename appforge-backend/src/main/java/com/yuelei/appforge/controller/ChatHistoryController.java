package com.yuelei.appforge.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yuelei.appforge.annotation.AuthCheck;
import com.yuelei.appforge.common.BaseResponse;
import com.yuelei.appforge.common.ResultUtils;
import com.yuelei.appforge.constant.UserConstant;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yuelei.appforge.model.entity.ChatHistory;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.ChatMemoryVO;
import com.yuelei.appforge.model.vo.ChatStatisticsVO;
import com.yuelei.appforge.service.ChatHistoryService;
import com.yuelei.appforge.service.ChatMemoryService;
import com.yuelei.appforge.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制层。
 *
 * @author yuelei
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired private ChatHistoryService chatHistoryService;

    @Autowired private UserService userService;
    @Autowired private ChatMemoryService chatMemoryService;

    /**
     * 保存。
     *
     * @param chatHistory
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Transactional
    public boolean save(@RequestBody ChatHistory chatHistory) {
        boolean result = chatHistoryService.save(chatHistory);
        if (result && chatHistory.getAppId() != null)
            chatMemoryService.invalidate(chatHistory.getAppId());
        return result;
    }

    /**
     * 根据主键删除。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Transactional
    public boolean remove(@PathVariable Long id) {
        ChatHistory previous = chatHistoryService.getById(id);
        boolean result = chatHistoryService.removeById(id);
        if (result && previous != null) chatMemoryService.invalidate(previous.getAppId());
        return result;
    }

    /**
     * 根据主键更新。
     *
     * @param chatHistory
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Transactional
    public boolean update(@RequestBody ChatHistory chatHistory) {
        ChatHistory previous = chatHistoryService.getById(chatHistory.getId());
        boolean result = chatHistoryService.updateById(chatHistory);
        if (result) {
            if (previous != null) chatMemoryService.invalidate(previous.getAppId());
            if (chatHistory.getAppId() != null
                    && (previous == null || !chatHistory.getAppId().equals(previous.getAppId())))
                chatMemoryService.invalidate(chatHistory.getAppId());
        }
        return result;
    }

    /**
     * 查询所有。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public List<ChatHistory> list() {
        return chatHistoryService.list();
    }

    /**
     * 根据主键获取。
     *
     * @param id 主键
     * @return 详情
     */
    @GetMapping("getInfo/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public ChatHistory getInfo(@PathVariable Long id) {
        return chatHistoryService.getById(id);
    }

    /**
     * 分页查询。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Page<ChatHistory> page(Page<ChatHistory> page) {
        return chatHistoryService.page(page);
    }

    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId 应用ID
     * @param pageSize 页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request 请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime lastCreateTime,
            @RequestParam(required = false) Long lastId,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result =
                chatHistoryService.listAppChatHistoryByPage(
                        appId, pageSize, lastCreateTime, lastId, loginUser);
        return ResultUtils.success(result);
    }

    @GetMapping("/app/{appId}/stats")
    public BaseResponse<ChatStatisticsVO> statistics(
            @PathVariable Long appId, HttpServletRequest request) {
        return ResultUtils.success(
                chatHistoryService.statistics(appId, userService.getLoginUser(request)));
    }

    @GetMapping("/app/{appId}/export")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable Long appId, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        // 权限错误先返回统一 JSON，避免提交下载响应头后才拒绝访问。
        chatHistoryService.requireChatAccess(appId, user);
        StreamingResponseBody body =
                output -> {
                    var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                    chatHistoryService.exportMarkdown(appId, user, writer);
                    writer.flush();
                };
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=app-" + appId + "-chat.md")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/app/{appId}/memory")
    public BaseResponse<ChatMemoryVO> memory(@PathVariable Long appId, HttpServletRequest request) {
        return ResultUtils.success(
                chatMemoryService.getStatus(appId, userService.getLoginUser(request)));
    }

    @PostMapping("/app/{appId}/memory/compress")
    public BaseResponse<ChatMemoryVO> compress(
            @PathVariable Long appId, HttpServletRequest request) {
        return ResultUtils.success(
                chatMemoryService.compress(appId, userService.getLoginUser(request)));
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(
            @RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        ThrowUtils.throwIf(
                pageNum < 1 || pageSize < 1 || pageSize > 50, ErrorCode.PARAMS_ERROR, "分页参数无效");
        // 查询数据
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result =
                chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }
}

