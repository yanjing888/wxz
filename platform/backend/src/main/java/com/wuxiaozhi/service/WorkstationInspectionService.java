package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.EnvCheckResponse;
import com.wuxiaozhi.entity.EnvCheckLog;
import com.wuxiaozhi.repository.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.wuxiaozhi.service.WorkstationService.fail;

@Service
public class WorkstationInspectionService {
    private final WorkstationService workstations;
    private final WorkstationFrames frames;
    private final LabWorkstationRepository stations;
    private final EnvCheckLogRepository logs;
    private final FileStorageService files;
    private final DifyService dify;
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"workstation-inspection");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    @Value("${wuxiaozhi.workstations.scheduling-enabled:true}") private boolean schedulingEnabled;
    public WorkstationInspectionService(WorkstationService w,WorkstationFrames f,LabWorkstationRepository s,EnvCheckLogRepository l,FileStorageService files,DifyService dify){workstations=w;frames=f;stations=s;logs=l;this.files=files;this.dify=dify;}
    public EnvCheckResponse inspect(Long id) {
        if(!running.add(id))throw fail(HttpStatus.CONFLICT,"该工位正在巡检，请稍候");
        try {
            var plan=workstations.prepareInspection(id);
            // 在 AI 调用前固定入座记录和采集时间。结束/换人不会把旧截图记到新学生名下。
            var frame=plan.frame();String url;
            try{url=files.storeImageBytes(frame.bytes(),".jpg");}catch(java.io.IOException e){throw fail(HttpStatus.SERVICE_UNAVAILABLE,"无法保存巡检截图");}
            var inputs=new LinkedHashMap<String,Object>();inputs.put("experiment",plan.room().getName());inputs.put("experiment_code",plan.experimentCode());inputs.put("snapshot_url",url);
            var result=dify.envCheck(inputs,"workstation-"+id,url);result.setSnapshotUrl(url);
            for(var use:plan.uses()) {
                var log=new EnvCheckLog();log.setSessionId(use.getSessionId());log.setWorkstationUseId(use.getId());log.setWorkstationId(id);log.setLessonId(use.getLessonId());log.setCameraCode(use.getCameraCode());log.setCapturedAt(frame.capturedAt());
                log.setLevel(result.getLevel()==null?"NA":result.getLevel());log.setSummary(result.getSummary());log.setSuggestion(result.getSuggestion());log.setSnapshotUrl(url);logs.save(log);
            }
            workstations.inspectionOutcome(id,result.isFromDify()?null:"已采集画面，AI 分析暂不可用");return result;
        }catch(org.springframework.web.server.ResponseStatusException e){workstations.inspectionOutcome(id,e.getReason());throw e;}
        catch(RuntimeException e){workstations.inspectionOutcome(id,"巡检失败，请稍后重试");throw e;}
        finally{running.remove(id);}
    }
    @Scheduled(fixedDelay=10000,initialDelay=20000)
    public void scheduledChecks() {
        if(!schedulingEnabled)return;
        for(var s:stations.findAll()) if(s.isEnabled()&&s.isInspectionEnabled()&&!running.contains(s.getId())&&
            (s.getLastInspectionAt()==null||s.getLastInspectionAt().isBefore(LocalDateTime.now().minusSeconds(60)))) {
            try{executor.execute(()->{try{inspect(s.getId());}catch(Exception ignored){/* 下次按间隔重试；不将无画面伪装为安全。 */}});}
            catch(RejectedExecutionException ignored){break;}
        }
    }
    @PreDestroy public void shutdown(){executor.shutdownNow();}
}
