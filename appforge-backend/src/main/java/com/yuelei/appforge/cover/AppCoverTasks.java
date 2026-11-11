package com.yuelei.appforge.cover;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.lang.TypeReference;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.entity.*;
import com.yuelei.appforge.model.vo.AppVO;
import com.yuelei.appforge.workspace.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Database authority for cover tasks. Lock app before task; never do browser or COS I/O inside a transaction. */
@Service
public class AppCoverTasks {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final CoverProperties config;
    Clock clock=Clock.systemUTC();private static final SecureRandom RANDOM=new SecureRandom();
    public AppCoverTasks(JdbcTemplate jdbc,TransactionTemplate tx,CoverProperties config){this.jdbc=jdbc;this.tx=tx;this.config=config;}
    public boolean enabled(){return config.isEnabled();}
    LocalDateTime now(){return LocalDateTime.now(clock);}
    static long n(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Number number?number.longValue():0;}
    static LocalDateTime time(Map<String,Object> row,String key){Object value=row.get(key);if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();throw new IllegalStateException("任务时间字段无效");}
    private Map<String,Object> app(long id,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM app WHERE id=? AND isDelete=0"+(lock?" FOR UPDATE":""),id);
        ThrowUtils.throwIf(rows.isEmpty(),ErrorCode.NOT_FOUND_ERROR,"应用不存在");return rows.getFirst();
    }
    private static App policy(Map<String,Object> row){App a=new App();a.setId(n(row,"id"));a.setUserId(n(row,"userId"));a.setPriority((int)n(row,"priority"));a.setVisibility((String)row.get("visibility"));return a;}
    private void requireEnabled(){ThrowUtils.throwIf(!enabled(),ErrorCode.OPERATION_ERROR,"自动封面未启用，请先完成数据库升级和 COS 配置");}
    @jakarta.annotation.PostConstruct public void validateConfiguration(){
        if(!enabled())return;
        if(config.getQueueCapacity()<1||config.getQueueCapacity()>200||config.getMaxAttempts()<1||config.getMaxAttempts()>5||config.getAttemptTimeoutSeconds()<10||config.getLeaseSeconds()<config.getAttemptTimeoutSeconds()+20)throw new IllegalStateException("封面队列、重试或租约配置无效");
        for(String address:List.of(config.getBaseUrl(),config.getPublicBaseUrl())){URI uri=URI.create(address);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalStateException("封面服务地址配置无效");}
        for(String host:config.getImageHosts())if(!host.matches("[a-zA-Z0-9.-]+")||host.startsWith(".")||host.contains("..")||host.matches("[0-9.]+")||host.equalsIgnoreCase("localhost")||host.toLowerCase(Locale.ROOT).endsWith(".localhost"))throw new IllegalStateException("图片域名必须是明确的受信任公开域名");
        for(String argument:config.getChromeArguments())if(argument.startsWith("--user-data-dir")||argument.startsWith("--profile-directory"))throw new IllegalStateException("截图不能使用已有浏览器配置目录");
        try{jdbc.queryForList("SELECT coverRevision,coverObjectKey FROM app LIMIT 0");jdbc.queryForList("SELECT id FROM app_cover_task LIMIT 0");jdbc.queryForList("SELECT id FROM app_cover_cleanup LIMIT 0");}catch(org.springframework.dao.DataAccessException e){throw new IllegalStateException("启用封面前，请执行 sql/migrations/20261110_app_cover.sql",e);}
    }
    public void enqueue(long appId,int version,String trigger){if(enabled())tx.executeWithoutResult(s->create(appId,version,trigger,null,null,false));}
    private Long create(long appId,int version,String trigger,String requestId,Long userId,boolean force){
        var a=app(appId,true);if(version<=0||n(a,"currentVersion")!=version||"MANUAL".equals(a.get("coverMode")))return null;
        if(!force&&n(a,"coverVersion")==version&&a.get("coverObjectKey")!=null)return n(a,"coverTaskId");
        var prior=jdbc.queryForList("SELECT id,versionNo,status FROM app_cover_task WHERE id=?",n(a,"coverTaskId"));
        if(!force&&!prior.isEmpty()&&n(prior.getFirst(),"versionNo")==version&&!("DEPLOY".equals(trigger)&&"FAILED".equals(prior.getFirst().get("status"))))return n(prior.getFirst(),"id");
        long revision=n(a,"coverRevision")+1;cancel(appId);
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO app_cover_task(appId,versionNo,revision,triggerType,requestId,requestedBy,nextRunTime,createTime,updateTime) VALUES(?,?,?,?,?,?,?,?,?)",new String[]{"id"});p.setLong(1,appId);p.setInt(2,version);p.setLong(3,revision);p.setString(4,trigger);p.setString(5,requestId);p.setObject(6,userId);p.setObject(7,now());p.setObject(8,now());p.setObject(9,now());return p;},keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue();jdbc.update("UPDATE app SET coverRevision=?,coverTaskId=?,coverStatus='PENDING' WHERE id=?",revision,id,appId);return id;
    }
    public Map<String,Object> regenerate(long id,int version,String requestId,User user){
        requireEnabled();ThrowUtils.throwIf(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,64}"),ErrorCode.PARAMS_ERROR,"请求标识无效");
        return tx.execute(s->{var a=app(id,true);AppPolicy.manage(policy(a),user);
            var prior=jdbc.queryForList("SELECT * FROM app_cover_task WHERE appId=? AND requestId=?",id,requestId);
            if(!prior.isEmpty()){ThrowUtils.throwIf(n(prior.getFirst(),"versionNo")!=version,ErrorCode.PARAMS_ERROR,"相同请求标识不能用于不同版本");return response(prior.getFirst());}
            ThrowUtils.throwIf(version<=0||n(a,"currentVersion")!=version,ErrorCode.OPERATION_ERROR,"版本已变化，请刷新后重试");
            ThrowUtils.throwIf("MANUAL".equals(a.get("coverMode")),ErrorCode.OPERATION_ERROR,"请先切换为自动截图");
            jdbc.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE",user.getId());
            int count=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM app_cover_task WHERE requestedBy=? AND createTime>?",Integer.class,user.getId(),now().minusMinutes(1)));
            int recent=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM app_cover_task WHERE appId=? AND triggerType='MANUAL' AND createTime>?",Integer.class,id,now().minusSeconds(30)));
            ThrowUtils.throwIf(count>=5||recent>0,ErrorCode.OPERATION_ERROR,"请求较频繁，请稍后再试");
            Long task=create(id,version,"MANUAL",requestId,user.getId(),true);return response(jdbc.queryForMap("SELECT * FROM app_cover_task WHERE id=?",task));
        });
    }
    private Map<String,Object> response(Map<String,Object> t){return Map.of("taskId",String.valueOf(n(t,"id")),"status",t.get("status"));}
    public void setMode(long id,String mode,String url,User user){
        requireEnabled();ThrowUtils.throwIf(!Set.of("AUTO","MANUAL").contains(mode==null?"":mode),ErrorCode.PARAMS_ERROR,"封面模式无效");if("MANUAL".equals(mode))validateUrl(url);
        tx.executeWithoutResult(s->{var a=app(id,true);AppPolicy.manage(policy(a),user);
            if("MANUAL".equals(mode)){cancel(id);cleanupLater((String)a.get("coverObjectKey"),now().plusHours(24));jdbc.update("UPDATE app SET coverMode='MANUAL',cover=?,coverObjectKey=NULL,coverVersion=0,coverTaskId=NULL,coverStatus='NONE',coverRevision=coverRevision+1 WHERE id=?",url==null?"":url.trim(),id);}
            else{jdbc.update("UPDATE app SET coverMode='AUTO',coverRevision=coverRevision+1 WHERE id=?",id);if(n(a,"currentVersion")<=0)jdbc.update("UPDATE app SET coverTaskId=NULL,coverStatus='NONE' WHERE id=?",id);create(id,(int)n(a,"currentVersion"),"REPAIR",null,null,true);}
        });
    }
    static void validateUrl(String value){if(value==null||value.isBlank())return;try{URI u=URI.create(value.trim());if(value.length()<=512&&Set.of("http","https").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null)return;}catch(IllegalArgumentException ignored){}throw new BusinessException(ErrorCode.PARAMS_ERROR,"封面地址必须是 HTTP 或 HTTPS 链接");}
    public void manualChanged(long id){if(!enabled())return;tx.executeWithoutResult(s->{var a=app(id,true);cancel(id);cleanupLater((String)a.get("coverObjectKey"),now().plusHours(24));jdbc.update("UPDATE app SET coverMode='MANUAL',coverObjectKey=NULL,coverVersion=0,coverTaskId=NULL,coverStatus='NONE',coverRevision=coverRevision+1 WHERE id=?",id);});}
    public boolean isCurrentImageUrl(long id,String value){if(!enabled()||value==null)return false;String prefix=config.getPublicBaseUrl().replaceAll("/+$","")+"/app/cover/image?appId="+id+"&revision=";return value.startsWith(prefix)&&value.substring(prefix.length()).matches("[a-f0-9]{16}");}
    private String imageUrl(long id,String key){return config.getPublicBaseUrl().replaceAll("/+$","")+"/app/cover/image?appId="+id+"&revision="+hash(key).substring(0,16);}
    public void deleted(long id){if(!enabled())return;var a=app(id,true);cancel(id);cleanupLater((String)a.get("coverObjectKey"),now());}
    private void cancel(long id){jdbc.update("UPDATE app_cover_task SET status='CANCELLED',executionToken=NULL,renderTokenHash=NULL,leaseUntil=NULL,updateTime=? WHERE appId=? AND status IN ('PENDING','RUNNING','RETRY_WAIT')",now(),id);}
    public List<Long> due(int count){if(!enabled()||count<=0)return List.of();return jdbc.queryForList("SELECT id FROM app_cover_task WHERE status IN ('PENDING','RETRY_WAIT') AND nextRunTime<=? ORDER BY nextRunTime,id LIMIT ?",Long.class,now(),count);}
    public record Attempt(long id,long appId,int version,long revision,String execution,String renderToken,String objectKey){}
    private boolean target(Map<String,Object>a,Map<String,Object>t){return "AUTO".equals(a.get("coverMode"))&&n(a,"currentVersion")==n(t,"versionNo")&&n(a,"coverRevision")==n(t,"revision")&&n(a,"coverTaskId")==n(t,"id");}
    private boolean live(Map<String,Object>t,String execution){return "RUNNING".equals(t.get("status"))&&Objects.equals(execution,t.get("executionToken"))&&t.get("leaseUntil")!=null&&time(t,"leaseUntil").isAfter(now());}
    public Attempt claim(long id,String worker){
        if(!enabled())return null;var initial=jdbc.queryForList("SELECT appId FROM app_cover_task WHERE id=?",id);if(initial.isEmpty())return null;
        return tx.execute(s->{long appId=n(initial.getFirst(),"appId");Map<String,Object>a;try{a=app(appId,true);}catch(BusinessException e){return null;}
            var rows=jdbc.queryForList("SELECT * FROM app_cover_task WHERE id=? FOR UPDATE",id);if(rows.isEmpty())return null;var t=rows.getFirst();
            if(!Set.of("PENDING","RETRY_WAIT").contains(t.get("status"))||time(t,"nextRunTime").isAfter(now()))return null;
            if(!target(a,t)){jdbc.update("UPDATE app_cover_task SET status='CANCELLED' WHERE id=?",id);return null;}
            String execution=UUID.randomUUID().toString();byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String key="app-covers/"+appId+"/v"+n(t,"versionNo")+"/"+n(t,"revision")+"/"+execution+".jpg";
            jdbc.update("UPDATE app_cover_task SET status='RUNNING',attempts=attempts+1,executionToken=?,workerId=?,leaseUntil=?,renderTokenHash=?,renderTokenExpiresAt=?,objectKey=?,errorCode='',errorMessage='',updateTime=? WHERE id=?",execution,worker,now().plusSeconds(config.getLeaseSeconds()),hash(token),now().plusSeconds(config.getLeaseSeconds()),key,now(),id);
            jdbc.update("UPDATE app SET coverStatus='RUNNING' WHERE id=?",appId);cleanupLater(key,now().plusHours(24));return new Attempt(id,appId,(int)n(t,"versionNo"),n(t,"revision"),execution,token,key);
        });
    }
    public boolean renew(Attempt a){return jdbc.update("UPDATE app_cover_task SET leaseUntil=?,updateTime=? WHERE id=? AND status='RUNNING' AND executionToken=? AND leaseUntil>?",now().plusSeconds(config.getLeaseSeconds()),now(),a.id(),a.execution(),now())==1;}
    public boolean publish(Attempt attempt){return Boolean.TRUE.equals(tx.execute(s->{Map<String,Object>a;try{a=app(attempt.appId(),true);}catch(BusinessException e){return false;}var t=jdbc.queryForMap("SELECT * FROM app_cover_task WHERE id=? FOR UPDATE",attempt.id());if(!target(a,t)||!live(t,attempt.execution()))return false;
        String old=(String)a.get("coverObjectKey");jdbc.update("UPDATE app SET coverObjectKey=?,cover='',coverVersion=?,coverStatus='SUCCEEDED' WHERE id=?",attempt.objectKey(),attempt.version(),attempt.appId());
        jdbc.update("UPDATE app_cover_task SET status='SUCCEEDED',executionToken=NULL,renderTokenHash=NULL,leaseUntil=NULL,errorCode='',errorMessage='',updateTime=? WHERE id=?",now(),attempt.id());jdbc.update("DELETE FROM app_cover_cleanup WHERE objectKey=?",attempt.objectKey());cleanupLater(old,now().plusHours(24));return true;}));}
    public void failed(Attempt attempt,String code,boolean retryable){tx.executeWithoutResult(s->{Map<String,Object>a;try{a=app(attempt.appId(),true);}catch(BusinessException e){return;}var t=jdbc.queryForMap("SELECT * FROM app_cover_task WHERE id=? FOR UPDATE",attempt.id());if(!live(t,attempt.execution()))return;
        boolean retry=retryable&&n(t,"attempts")<config.getMaxAttempts();String status=retry?"RETRY_WAIT":"FAILED";
        jdbc.update("UPDATE app_cover_task SET status=?,executionToken=NULL,renderTokenHash=NULL,leaseUntil=NULL,errorCode=?,errorMessage=?,nextRunTime=?,updateTime=? WHERE id=?",status,code.substring(0,Math.min(64,code.length())),retry?"截图暂时失败，稍后自动重试":"封面生成失败，请检查截图环境与 COS 配置",now().plusSeconds(n(t,"attempts")==1?30:120),now(),attempt.id());if(target(a,t))jdbc.update("UPDATE app SET coverStatus=? WHERE id=?",status,attempt.appId());});}
    public void recover(){if(!enabled())return;for(Long id:jdbc.queryForList("SELECT id FROM app_cover_task WHERE status='RUNNING' AND leaseUntil<=? LIMIT 20",Long.class,now())){var initial=jdbc.queryForMap("SELECT appId FROM app_cover_task WHERE id=?",id);tx.executeWithoutResult(s->{Map<String,Object>a;try{a=app(n(initial,"appId"),true);}catch(BusinessException e){jdbc.update("UPDATE app_cover_task SET status='CANCELLED',executionToken=NULL,renderTokenHash=NULL WHERE id=?",id);return;}var t=jdbc.queryForMap("SELECT * FROM app_cover_task WHERE id=? FOR UPDATE",id);if(!"RUNNING".equals(t.get("status"))||time(t,"leaseUntil").isAfter(now()))return;String status=target(a,t)?(n(t,"attempts")<config.getMaxAttempts()?"RETRY_WAIT":"FAILED"):"CANCELLED";jdbc.update("UPDATE app_cover_task SET status=?,executionToken=NULL,renderTokenHash=NULL,leaseUntil=NULL,nextRunTime=?,updateTime=? WHERE id=?",status,now(),now(),id);if(target(a,t))jdbc.update("UPDATE app SET coverStatus=? WHERE id=?",status,n(a,"id"));});}}
    public void repairMissing(){if(!enabled())return;for(var a:jdbc.queryForList("SELECT id,currentVersion FROM app WHERE isDelete=0 AND coverMode='AUTO' AND currentVersion>0 AND (coverTaskId IS NULL OR coverTaskId NOT IN (SELECT id FROM app_cover_task)) ORDER BY id LIMIT 10"))enqueue(n(a,"id"),(int)n(a,"currentVersion"),"REPAIR");}
    private void cleanupLater(String key,LocalDateTime when){if(key==null||!key.startsWith("app-covers/"))return;if(Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM app_cover_cleanup WHERE objectKey=?",Integer.class,key))==0)jdbc.update("INSERT INTO app_cover_cleanup(objectKey,notBefore,nextRunTime) VALUES(?,?,?)",key,when,when);}
    public List<Map<String,Object>> cleanupDue(){return jdbc.queryForList("SELECT * FROM app_cover_cleanup WHERE notBefore<=? AND nextRunTime<=? ORDER BY id LIMIT 20",now(),now());}
    public boolean referenced(String key){return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM app WHERE isDelete=0 AND coverObjectKey=?",Integer.class,key))>0||Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM app_cover_task WHERE objectKey=? AND status='RUNNING' AND leaseUntil>?",Integer.class,key,now()))>0;}
    public void cleaned(long id){jdbc.update("DELETE FROM app_cover_cleanup WHERE id=?",id);}
    public void cleanupFailed(long id){jdbc.update("UPDATE app_cover_cleanup SET attempts=attempts+1,nextRunTime=? WHERE id=?",now().plusMinutes(5),id);}
    public void retainHistory(){if(enabled())jdbc.update("DELETE FROM app_cover_task WHERE status IN ('FAILED','CANCELLED','SUCCEEDED') AND updateTime<? AND id NOT IN (SELECT coverTaskId FROM app WHERE coverTaskId IS NOT NULL) AND (objectKey IS NULL OR objectKey NOT IN (SELECT objectKey FROM app_cover_cleanup))",now().minusDays(30));}
    public Map<String,Object> status(long id,User user){var a=app(id,false);AppPolicy.manage(policy(a),user);if(!enabled())return Map.of("enabled",false,"status","NONE");Map<String,Object> out=new LinkedHashMap<>();out.put("enabled",true);out.put("status",a.get("coverStatus"));out.put("mode",a.get("coverMode"));out.put("version",a.get("coverVersion"));out.put("targetVersion",a.get("currentVersion"));out.put("revision",String.valueOf(n(a,"coverRevision")));var rows=jdbc.queryForList("SELECT errorCode,errorMessage FROM app_cover_task WHERE id=?",n(a,"coverTaskId"));if(!rows.isEmpty())out.putAll(rows.getFirst());return out;}
    public void decorate(List<AppVO> views){if(!enabled()||views.isEmpty())return;Map<Long,AppVO> byId=new HashMap<>();views.forEach(v->byId.put(v.getId(),v));String args=String.join(",",Collections.nCopies(byId.size(),"?"));for(var row:jdbc.queryForList("SELECT id,coverMode,coverStatus,coverVersion,coverRevision,coverObjectKey FROM app WHERE id IN ("+args+")",byId.keySet().toArray())){var vo=byId.get(n(row,"id"));vo.setCoverEnabled(true);vo.setCoverMode((String)row.get("coverMode"));vo.setCoverStatus((String)row.get("coverStatus"));vo.setCoverVersion((int)n(row,"coverVersion"));vo.setCoverRevision(String.valueOf(n(row,"coverRevision")));if("AUTO".equals(row.get("coverMode"))&&row.get("coverObjectKey") instanceof String key)vo.setCover(imageUrl(vo.getId(),key));}}
    public String readableKey(long id,User user){requireEnabled();var a=app(id,false);ThrowUtils.throwIf(!AppPolicy.canRead(policy(a),user),ErrorCode.NOT_FOUND_ERROR);String key=(String)a.get("coverObjectKey");ThrowUtils.throwIf(!"AUTO".equals(a.get("coverMode"))||key==null,ErrorCode.NOT_FOUND_ERROR);return key;}
    public record Rendered(String html,String marker,String csp){}
    public Rendered render(long id,String token){
        requireEnabled();ThrowUtils.throwIf(token==null||token.length()>128,ErrorCode.NOT_FOUND_ERROR);var rows=jdbc.queryForList("SELECT * FROM app_cover_task WHERE id=?",id);ThrowUtils.throwIf(rows.isEmpty(),ErrorCode.NOT_FOUND_ERROR);var t=rows.getFirst();var a=app(n(t,"appId"),false);
        boolean authenticated=t.get("renderTokenHash") instanceof String stored&&MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),hash(token).getBytes(StandardCharsets.US_ASCII));
        ThrowUtils.throwIf(!authenticated||!target(a,t)||!live(t,(String)t.get("executionToken"))||t.get("renderTokenExpiresAt")==null||!time(t,"renderTokenExpiresAt").isAfter(now()),ErrorCode.NOT_FOUND_ERROR);
        var source=jdbc.queryForList("SELECT codeFiles FROM app_version WHERE appId=? AND versionNo=?",String.class,n(t,"appId"),n(t,"versionNo"));ThrowUtils.throwIf(source.isEmpty(),ErrorCode.NOT_FOUND_ERROR);
        Map<String,String> files=JSONUtil.toBean(source.getFirst(),new TypeReference<Map<String,String>>(){},false);
        if(com.yuelei.appforge.workspace.vue.VueProjectFiles.isProject(files)){Map<String,String> runtime=new LinkedHashMap<>();files.forEach((p,v)->{if(p.startsWith("dist/"))runtime.put(p.substring(5),v);});files=runtime;}
        var document=org.jsoup.Jsoup.parse(AppPageRenderer.inlinePrivateAssets(files));document.outputSettings().prettyPrint(false);
        // Screenshot-only history storage: srcdoc has an opaque origin. Vue Router otherwise falls back to a forbidden HTTP navigation.
        document.head().prependElement("script").appendChild(new org.jsoup.nodes.DataNode("(()=>{let state=null;Object.defineProperty(history,'state',{configurable:true,get:()=>state});history.replaceState=(s)=>{state=s};history.pushState=(s)=>{state=s};})();"));
        String marker=id+":"+t.get("executionToken");String html="<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"appforge-cover\" content=\""+HtmlUtil.escape(marker)+"\"><style>html,body{margin:0;width:100%;height:100%;overflow:hidden}iframe{width:100%;height:100%;border:0;display:block}</style></head><body><iframe id=\"cover-content\" sandbox=\"allow-scripts\" referrerpolicy=\"no-referrer\" srcdoc=\""+HtmlUtil.escape(document.outerHtml())+"\"></iframe></body></html>";
        String hosts=config.getImageHosts().stream().map(h->"https://"+h).collect(java.util.stream.Collectors.joining(" "));
        String csp="default-src 'none'; script-src 'unsafe-inline' data:; style-src 'unsafe-inline' data:; img-src data: "+hosts+"; font-src data: "+hosts+"; connect-src 'none'; frame-src about:; object-src 'none'; base-uri 'none'; form-action 'none'; sandbox allow-scripts";
        return new Rendered(html,marker,csp);
    }
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
