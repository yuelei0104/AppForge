package com.yuelei.appforge.controller;

import com.yuelei.appforge.common.*;
import com.yuelei.appforge.cover.*;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.service.UserService;
import com.yuelei.appforge.workspace.AppPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController @RequiredArgsConstructor
public class AppCoverController {
    private final AppCoverTasks tasks;private final AppCoverWorker worker;private final CoverObjectStore objects;private final UserService users;
    public record Request(Long appId,Integer expectedVersion,String requestId,String mode,String url){}
    @GetMapping("/app/cover/status") public BaseResponse<Map<String,Object>> status(@RequestParam long appId,HttpServletRequest request){var result=new LinkedHashMap<>(tasks.status(appId,users.getLoginUser(request)));result.put("queuePaused",worker.paused());return ResultUtils.success(result);}
    @PostMapping("/app/cover/regenerate") public BaseResponse<Map<String,Object>> regenerate(@RequestBody Request body,HttpServletRequest request){ThrowUtils.throwIf(body==null||body.appId()==null||body.expectedVersion()==null,ErrorCode.PARAMS_ERROR);return ResultUtils.success(tasks.regenerate(body.appId(),body.expectedVersion(),body.requestId(),users.getLoginUser(request)));}
    @PostMapping("/app/cover/mode") public BaseResponse<Boolean> mode(@RequestBody Request body,HttpServletRequest request){ThrowUtils.throwIf(body==null||body.appId()==null,ErrorCode.PARAMS_ERROR);tasks.setMode(body.appId(),body.mode(),body.url(),users.getLoginUser(request));return ResultUtils.success(true);}
    @PostMapping("/app/cover/admin/resume") public BaseResponse<Boolean> resume(HttpServletRequest request){ThrowUtils.throwIf(!AppPolicy.isAdmin(users.getLoginUser(request)),ErrorCode.NO_AUTH_ERROR);worker.resume();return ResultUtils.success(true);}
    @GetMapping("/app/cover/image") public ResponseEntity<byte[]> image(@RequestParam long appId,HttpServletRequest request){User viewer=null;try{viewer=users.getLoginUser(request);}catch(BusinessException ignored){}try{return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(objects.read(tasks.readableKey(appId,viewer)));}catch(BusinessException e){return ResponseEntity.notFound().build();}}
    @GetMapping("/internal/cover/render/{taskId}") public ResponseEntity<String> render(@PathVariable long taskId,@RequestParam(required=false) String token){try{var result=tasks.render(taskId,token);return ResponseEntity.ok().contentType(new MediaType("text","html",StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore()).header("X-AppForge-Cover",result.marker()).header("Referrer-Policy","no-referrer").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy",result.csp()).body(result.html());}catch(BusinessException e){return ResponseEntity.notFound().build();}}
}
