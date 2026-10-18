package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.WorkstationRequest;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WorkstationService {
    private final LabRoomRepository rooms;
    private final LabWorkstationRepository stations;
    private final LabLessonRepository lessons;
    private final WorkstationUseRepository uses;
    private final LabSessionRepository sessions;
    private final UserRepository users;
    private final ClassRosterService roster;
    private final ExperimentConfigService experiments;
    private final EnvCheckLogRepository logs;
    private final WorkstationFrames frames;
    private final com.wuxiaozhi.config.LabDeploymentProperties deployment;
    public WorkstationService(LabRoomRepository rooms,LabWorkstationRepository stations,LabLessonRepository lessons,
        WorkstationUseRepository uses,LabSessionRepository sessions,UserRepository users,ClassRosterService roster,
        ExperimentConfigService experiments,EnvCheckLogRepository logs,WorkstationFrames frames,com.wuxiaozhi.config.LabDeploymentProperties deployment) {
        this.rooms=rooms;this.stations=stations;this.lessons=lessons;this.uses=uses;this.sessions=sessions;
        this.users=users;this.roster=roster;this.experiments=experiments;this.logs=logs;this.frames=frames;this.deployment=deployment;
    }
    @EventListener(ApplicationReadyEvent.class) @Transactional
    public void initializeRooms() {
        for(var exp:experiments.listAll()) if(rooms.findByExperimentCode(exp.getCode()).isEmpty()) {
            var room=new LabRoom();room.setExperimentCode(exp.getCode());room.setName(exp.getName()+"实验室");rooms.save(room);
        }
        // 显式开启才首次登记现场设备；重启不覆盖管理端配置，也不重置终端绑定。
        if(deployment.isBootstrapEnabled()&&!text(deployment.getInitialStreamUrl()).isBlank()) {
            String stream=text(deployment.getInitialStreamUrl());
            if(stations.findAll().stream().noneMatch(s->"deployment-camera".equals(s.getCameraCode())||hash(stream).equals(s.getStreamHash()))) {
                String name=text(deployment.getBootstrapRoomName()),code=text(deployment.getBootstrapStationCode());
                if(name.isBlank()||name.length()>100||code.isBlank()||code.length()>32)throw new IllegalStateException("请检查 lab.yml 中的首次登记实验室和工位编号");
                var target=rooms.findAll().stream().filter(r->name.equals(r.getName())).findFirst().orElseGet(()->{var r=new LabRoom();r.setName(name);return rooms.save(r);});
                if(stations.findByRoomIdOrderByCodeAsc(target.getId()).stream().anyMatch(s->code.equalsIgnoreCase(s.getCode())))throw new IllegalStateException("首次登记工位编号已存在，请关闭 bootstrap-enabled 后在管理端接入设备");
                var req=new WorkstationRequest(code,code+" 号实验台",2,true,"deployment-camera","NETWORK",stream,deployment.getInitialSnapshotUrl(),deployment.getCameraUsername(),deployment.getCameraPassword());
                validateCamera(req);
                var s=new LabWorkstation();s.setRoomId(target.getId());s.setCode(code);s.setName(req.name());s.setCapacity(2);s.setCameraCode(req.cameraCode());s.setStreamUrl(stream);s.setStreamHash(hash(stream));s.setSnapshotUrl(req.snapshotUrl());s.setCameraUsername(req.cameraUsername());s.setCameraPassword(req.cameraPassword());stations.save(s);
            }
        }
    }
    public static ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    public User teacher(Long id) {
        var user=users.findById(id).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"无权访问"));
        if(!user.isEnabled()||!Set.of(UserRole.TEACHER,UserRole.ADMIN).contains(user.getRole()))throw fail(HttpStatus.FORBIDDEN,"需要教师或管理员权限");return user;
    }
    private void deviceManager(User teacher) {
        if(!UserRole.ADMIN.equals(teacher.getRole()))throw fail(HttpStatus.FORBIDDEN,"设备登记和终端配对由管理员维护");
    }
    public List<LabRoom> listRooms(Long userId) { teacher(userId); return rooms.findAll(); }
    public Map<String,Object> overview(Long userId) {
        var user=teacher(userId);
        var allRooms=rooms.findAll();
        var roomNames=new HashMap<Long,String>();allRooms.forEach(r->roomNames.put(r.getId(),r.getName()));
        var rows=stations.findAll().stream().sorted(Comparator.comparing(LabWorkstation::getRoomId).thenComparing(LabWorkstation::getCode)).map(s->{
            var row=new LinkedHashMap<String,Object>();
            row.put("id",s.getId());row.put("code",s.getCode());row.put("name",s.getName());row.put("roomId",s.getRoomId());row.put("roomName",roomNames.get(s.getRoomId()));
            row.put("enabled",s.isEnabled());row.put("cameraType",s.getCameraType());row.put("streamConfigured",!text(s.getStreamUrl()).isBlank());row.put("frameFresh",frames.fresh(s.getId()));row.put("canView",true);
            // 配置修改后通知播放器重连；不向教师端暴露原始设备地址或凭据。
            row.put("streamRevision",hash(text(s.getStreamUrl())+":"+text(s.getCameraType())));
            return row;
        }).toList();
        return Map.of("rooms",allRooms,"stations",rows,"canManageDevices",UserRole.ADMIN.equals(user.getRole()));
    }
    @Transactional
    public Map<String,Object> saveSimple(Long adminId,Long roomId,Long id,com.wuxiaozhi.dto.SimpleWorkstationRequest req) {
        deviceManager(teacher(adminId));
        var current=id==null?null:station(id);
        String address=text(req.address());
        if(!address.isBlank()&&!address.contains("://")&&!address.startsWith("/ws/")&&!address.startsWith("/ws-direct/")) {
            String template=text(deployment.getIpStreamTemplate());
            if(!template.contains("{host}"))throw fail(HttpStatus.BAD_REQUEST,"本部署未配置该型号的 IP 播放路径，请填写完整的视频流地址");
            try {
                var host=java.net.URI.create("http://"+address);
                if(host.getHost()==null||host.getUserInfo()!=null||!text(host.getPath()).isBlank()||host.getQuery()!=null||host.getFragment()!=null||host.getPort()>65535)throw new IllegalArgumentException();
            }catch(IllegalArgumentException e){throw fail(HttpStatus.BAD_REQUEST,"请填写有效的摄像头 IP 或主机地址，可附带端口");}
            address=template.replace("{host}",address);
        }
        String type=current!=null&&"USB".equals(current.getCameraType())&&address.isBlank()?"USB":"NETWORK";
        var full=new WorkstationRequest(req.code(),req.name(),current==null?1:current.getCapacity(),req.enabled(),current==null?null:current.getCameraCode(),type,address,current==null?"":current.getSnapshotUrl(),current==null?"":current.getCameraUsername(),null);
        return saveStation(adminId,null,roomId,id,full);
    }
    @Transactional
    public Map<String,Object> importDeploymentCamera(Long adminId,Long roomId,String code) {
        deviceManager(teacher(adminId));
        if(text(deployment.getInitialStreamUrl()).isBlank())throw fail(HttpStatus.CONFLICT,"请先在 config/lab.yml 中配置 initial-stream-url，重启后接入");
        if(text(code).isBlank()||text(code).length()>32)throw fail(HttpStatus.BAD_REQUEST,"工位编号应为 1 至 32 个字符");
        var req=new WorkstationRequest(text(code),text(code)+" 号实验台",2,true,"deployment-camera","NETWORK",deployment.getInitialStreamUrl(),deployment.getInitialSnapshotUrl(),deployment.getCameraUsername(),deployment.getCameraPassword());
        return saveStation(adminId,null,roomId,null,req);
    }
    @Transactional
    public LabRoom createRoom(Long adminId,String name) {
        deviceManager(teacher(adminId));
        if(text(name).isBlank() || text(name).length()>100)throw fail(HttpStatus.BAD_REQUEST,"请填写有效的实验室名称");
        var r=new LabRoom();r.setName(text(name));return rooms.save(r);
    }
    private LabRoom room(String code,Long roomId) {
        return roomId==null?room(code):rooms.findById(roomId).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"实验室不存在"));
    }
    private String lessonExperiment(LabLesson lesson) {
        return lesson.getExperimentCode()!=null?lesson.getExperimentCode():rooms.findById(lesson.getRoomId()).orElseThrow().getExperimentCode();
    }
    private LabRoom room(String code) {
        return rooms.findByExperimentCode(code).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"实验室不存在"));
    }
    public LabWorkstation station(Long id) {return stations.findById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"工位不存在"));}
    public Map<String,Object> config(Long teacherId,Long id) {
        deviceManager(teacher(teacherId));var s=station(id);
        return Map.of("streamUrl",text(s.getStreamUrl()),"snapshotUrl",text(s.getSnapshotUrl()),"cameraUsername",text(s.getCameraUsername()));
    }
    private LabRoom lockRoom(Long id){return rooms.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"实验室不存在"));}
    private LabWorkstation lockStation(Long id){return stations.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"工位不存在"));}
    public LabLesson activeLesson(Long roomId){return lessons.findFirstByRoomIdAndStatusOrderByStartedAtDesc(roomId,"ACTIVE").orElse(null);}
    private boolean visible(User teacher,LabLesson lesson) {
        String scope=FeedbackService.managedClass(teacher);return scope.isBlank() || lesson!=null&&scope.equals(lesson.getClassName());
    }
    private void lessonAccess(User teacher,LabLesson lesson) {
        if(!visible(teacher,lesson))throw fail(HttpStatus.FORBIDDEN,"当前课次不属于您的教学班");
    }
    public void cameraAccess(Long userId,LabWorkstation station,String terminal) {
        var user=users.findById(userId).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"无权访问"));
        if(!user.isEnabled())throw fail(HttpStatus.FORBIDDEN,"账号已停用");
        if(Set.of(UserRole.ADMIN,UserRole.TEACHER).contains(user.getRole()))return;
        throw fail(HttpStatus.FORBIDDEN,"监控画面仅在教师端查看");
    }
    @Transactional
    public Map<String,Object> dashboard(Long teacherId,String code) {
        return dashboard(teacherId,code,null);
    }
    @Transactional
    public Map<String,Object> dashboard(Long teacherId,String code,Long roomId) {
        var teacher=teacher(teacherId);var room=room(code,roomId);var lesson=activeLesson(room.getId());
        var result=new LinkedHashMap<String,Object>();result.put("room",room);result.put("classes",roster.visibleClasses(teacher));
        result.put("canManageDevices",UserRole.ADMIN.equals(teacher.getRole()));
        result.put("lesson",visible(teacher,lesson)?lesson:null);result.put("lessonExperimentCode",lesson==null?null:lessonExperiment(lesson));result.put("otherClassInUse",lesson!=null&&!visible(teacher,lesson));
        result.put("stations",stations.findByRoomIdOrderByCodeAsc(room.getId()).stream().map(s->view(s,visible(teacher,lesson))).toList());return result;
    }
    private Map<String,Object> view(LabWorkstation s,boolean showPeople) {
        var row=new LinkedHashMap<String,Object>();
        row.put("id",s.getId());row.put("code",s.getCode());row.put("name",s.getName());row.put("capacity",s.getCapacity());row.put("enabled",s.isEnabled());
        row.put("cameraCode",s.getCameraCode());row.put("cameraType",s.getCameraType());row.put("streamConfigured",!text(s.getStreamUrl()).isBlank());
        row.put("snapshotConfigured",s.getSnapshotUrl()!=null&&!s.getSnapshotUrl().isBlank());row.put("paired",s.getTerminalHash()!=null);
        row.put("publisherConfigured",s.getPublisherHash()!=null);row.put("terminalSeenAt",s.getTerminalSeenAt());
        row.put("terminalOnline",s.getTerminalSeenAt()!=null&&s.getTerminalSeenAt().isAfter(LocalDateTime.now().minusSeconds(45)));
        row.put("frameFresh",frames.fresh(s.getId()));row.put("inspectionEnabled",s.isInspectionEnabled());row.put("canView",showPeople);
        row.put("inspectionError",showPeople?s.getInspectionError():null);row.put("lastInspectionAt",showPeople?s.getLastInspectionAt():null);
        var current=cleanUses(s.getId());row.put("occupied",!current.isEmpty());
        row.put("occupants",showPeople?current.stream().map(u->{var m=new LinkedHashMap<String,Object>();m.put("useId",u.getId());m.put("userId",u.getUserId());m.put("sessionId",u.getSessionId());m.put("studentName",u.getStudentName());m.put("username",users.findById(u.getUserId()).map(User::getUsername).orElse(""));m.put("className",u.getClassName());m.put("joinedAt",u.getJoinedAt());m.put("step",sessions.findById(u.getSessionId()).map(LabSession::getActiveStep).orElse(0));return m;}).toList():List.of());
        return row;
    }
    /** 结束实验只释放该学生，浏览器关闭不等于离座。 */
    private List<WorkstationUse> cleanUses(Long stationId) {
        var current=uses.findByStationIdAndEndedAtIsNull(stationId);var active=new ArrayList<WorkstationUse>();
        for(var use:current){var session=sessions.findById(use.getSessionId());
            if(session.isEmpty()||!"ACTIVE".equals(session.get().getStatus()))end(use,"EXPERIMENT_FINISHED");else active.add(use);}
        return active;
    }
    private void end(WorkstationUse use,String reason){use.setEndedAt(LocalDateTime.now());use.setEndReason(reason);uses.save(use);}
    private void empty(LabWorkstation s){if(!cleanUses(s.getId()).isEmpty())throw fail(HttpStatus.CONFLICT,"工位有人使用，请先结束使用再修改设备");}
    private static String text(String value){return value==null?"":value.trim();}
    private void validateCamera(WorkstationRequest request) {
        String stream=text(request.streamUrl()),snapshot=text(request.snapshotUrl());
        if(!stream.isBlank()&&!stream.matches("(?i)^(https?://|wss?://|/ws/|/ws-direct/).+"))throw fail(HttpStatus.BAD_REQUEST,"播放地址需为浏览器可播放的 FLV 地址，不能填写 RTSP 地址");
        if(!stream.isBlank()&&!stream.startsWith("/ws/")&&!stream.startsWith("/ws-direct/")) {
            try {var uri=java.net.URI.create(stream);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();}
            catch(IllegalArgumentException e){throw fail(HttpStatus.BAD_REQUEST,"播放地址无效，不能在地址中包含设备账号密码");}
        }
        if(!snapshot.isBlank())WorkstationFrames.validateSnapshotUrl(snapshot);
    }
    @Transactional
    public Map<String,Object> saveStation(Long teacherId,String code,Long id,WorkstationRequest req) {
        return saveStation(teacherId,code,null,id,req);
    }
    @Transactional
    public Map<String,Object> saveStation(Long teacherId,String code,Long roomId,Long id,WorkstationRequest req) {
        deviceManager(teacher(teacherId));var room=room(code,roomId);lockRoom(room.getId());validateCamera(req);
        var s=id==null?new LabWorkstation():lockStation(id);
        if(id!=null&&!s.getRoomId().equals(room.getId()))throw fail(HttpStatus.BAD_REQUEST,"工位不属于该实验室");
        if(id!=null)empty(s);
        for(var other:stations.findAll())if(!other.getId().equals(id)) {
            if(other.getRoomId().equals(room.getId())&&other.getCode().equalsIgnoreCase(text(req.code())))throw fail(HttpStatus.CONFLICT,"工位编号已存在");
            if(!text(req.cameraCode()).isBlank()&&text(other.getCameraCode()).equalsIgnoreCase(text(req.cameraCode())))throw fail(HttpStatus.CONFLICT,"摄像头编号已绑定其他工位");
            if(!text(req.streamUrl()).isBlank()&&text(req.streamUrl()).equals(text(other.getStreamUrl())))throw fail(HttpStatus.CONFLICT,"同一播放地址不能绑定多个工位");
        }
        s.setRoomId(room.getId());s.setCode(text(req.code()));s.setName(text(req.name()));s.setCapacity(req.capacity());s.setEnabled(req.enabled());
        boolean typeChanged=!Objects.equals(s.getCameraType(),req.cameraType());
        s.setCameraCode(text(req.cameraCode()).isBlank()?null:text(req.cameraCode()));s.setCameraType(req.cameraType());s.setStreamUrl(text(req.streamUrl()));s.setSnapshotUrl(text(req.snapshotUrl()));
        s.setStreamHash(text(req.streamUrl()).isBlank()?null:hash(text(req.streamUrl())));s.setInspectionError(null);
        s.setCameraUsername(text(req.cameraUsername()));
        if(req.cameraPassword()!=null)s.setCameraPassword(req.cameraPassword());
        if(typeChanged){s.setPublisherHash(null);frames.clear(s.getId());}
        stations.save(s);return view(s,true);
    }
    @Transactional
    public Map<String,Object> startLesson(Long teacherId,String code,String className) {
        return startLesson(teacherId,code,null,className);
    }
    @Transactional
    public Map<String,Object> startLesson(Long teacherId,String code,Long roomId,String className) {
        if(experiments.listAll().stream().noneMatch(e->e.getCode().equals(code)))throw fail(HttpStatus.BAD_REQUEST,"实验项目不存在");
        var teacher=teacher(teacherId);var room=room(code,roomId);lockRoom(room.getId());
        String cls=roster.scope(teacher,text(className));
        if(cls.isBlank()||"未分班".equals(cls)||!roster.visibleClasses(teacher).contains(cls))throw fail(HttpStatus.BAD_REQUEST,"请选择已有教学班");
        if(activeLesson(room.getId())!=null)throw fail(HttpStatus.CONFLICT,"实验室已有进行中的课次，请先结束课次");
        var lesson=new LabLesson();lesson.setRoomId(room.getId());lesson.setTeacherId(teacherId);lesson.setExperimentCode(code);lesson.setClassName(cls);lessons.save(lesson);
        return dashboard(teacherId,code,roomId);
    }
    @Transactional
    public void closeLesson(Long teacherId,String code) {
        closeLesson(teacherId,code,null);
    }
    @Transactional
    public void closeLesson(Long teacherId,String code,Long roomId) {
        var teacher=teacher(teacherId);var room=room(code,roomId);lockRoom(room.getId());var lesson=activeLesson(room.getId());
        if(lesson==null)return;lessonAccess(teacher,lesson);
        for(var s:stations.findByRoomIdOrderByCodeAsc(room.getId())){lockStation(s.getId());s.setInspectionEnabled(false);s.setGroupCodeHash(null);s.setGroupCodeExpiresAt(null);stations.save(s);}
        for(var use:uses.findByLessonIdAndEndedAtIsNull(lesson.getId()))end(use,"LESSON_CLOSED");
        lesson.setStatus("CLOSED");lesson.setEndedAt(LocalDateTime.now());lessons.save(lesson);
    }
    public static String hash(String token) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static String secret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    @Transactional
    public Map<String,Object> pairCode(Long teacherId,Long id) {
        deviceManager(teacher(teacherId));var s=lockStation(id);empty(s);
        String code=secret().substring(0,10);s.setPairHash(hash(code));s.setPairExpiresAt(LocalDateTime.now().plusMinutes(10));stations.save(s);
        return Map.of("code",code,"expiresAt",s.getPairExpiresAt());
    }
    @Transactional
    public Map<String,Object> pair(Long adminId,String code) {
        deviceManager(teacher(adminId));
        var found=stations.findByPairHash(hash(text(code))).orElseThrow(()->fail(HttpStatus.BAD_REQUEST,"配对码无效或已使用"));
        var s=lockStation(found.getId());empty(s);
        if(!hash(text(code)).equals(s.getPairHash())||s.getPairExpiresAt()==null||s.getPairExpiresAt().isBefore(LocalDateTime.now()))throw fail(HttpStatus.BAD_REQUEST,"配对码已失效");
        String token=secret();s.setTerminalHash(hash(token));s.setPairHash(null);s.setPairExpiresAt(null);s.setPairedAt(LocalDateTime.now());s.setTerminalSeenAt(LocalDateTime.now());stations.save(s);
        return Map.of("terminalToken",token,"stationId",s.getId());
    }
    public LabWorkstation terminal(String token) {
        if(token==null||token.isBlank())throw fail(HttpStatus.CONFLICT,"此终端尚未绑定工位");
        return stations.findByTerminalHash(hash(token)).orElseThrow(()->fail(HttpStatus.CONFLICT,"终端绑定已失效，请重新配对"));
    }
    @Transactional
    public Map<String,Object> terminalState(Long userId,String token) {
        var original=terminal(token);var s=lockStation(original.getId());
        if(!Objects.equals(s.getTerminalHash(),hash(token)))throw fail(HttpStatus.CONFLICT,"终端绑定已失效");
        s.setTerminalSeenAt(LocalDateTime.now());stations.save(s);
        var room=rooms.findById(s.getRoomId()).orElseThrow();var lesson=activeLesson(room.getId());
        var result=new LinkedHashMap<String,Object>();result.put("room",room);
        // 学生终端状态不包含视频地址、密码和其他同学的实验记录。
        result.put("station",Map.of("id",s.getId(),"code",s.getCode(),"name",s.getName(),"capacity",s.getCapacity(),"enabled",s.isEnabled(),"occupied",!cleanUses(s.getId()).isEmpty(),"occupantCount",cleanUses(s.getId()).size()));
        result.put("lesson",lesson);result.put("lessonExperimentCode",lesson==null?null:lessonExperiment(lesson));
        result.put("myUse",cleanUses(s.getId()).stream().filter(u->u.getUserId().equals(userId)).findFirst().orElse(null));return result;
    }
    @Transactional
    public WorkstationUse claim(Long userId,String token,Long sessionId) {
        var original=terminal(token);lockRoom(original.getRoomId());var s=lockStation(original.getId());
        if(!Objects.equals(s.getTerminalHash(),hash(token)))throw fail(HttpStatus.CONFLICT,"终端绑定已失效");
        return join(s,userId,sessionId,false);
    }
    @Transactional
    public LabRoom renameRoom(Long adminId,String code,String name) {
        return renameRoom(adminId,code,null,name);
    }
    @Transactional
    public LabRoom renameRoom(Long adminId,String code,Long roomId,String name) {
        deviceManager(teacher(adminId));var room=room(code,roomId);lockRoom(room.getId());
        if(name==null||name.isBlank()||name.trim().length()>100)throw fail(HttpStatus.BAD_REQUEST,"请填写有效的实验室名称");
        room.setName(name.trim());return rooms.save(room);
    }
    private WorkstationUse join(LabWorkstation s,Long userId,Long sessionId,boolean group) {
        if(!s.isEnabled()||(!group&&s.getTerminalHash()==null))throw fail(HttpStatus.CONFLICT,"工位未启用或终端未配对");
        var lesson=activeLesson(s.getRoomId());if(lesson==null)throw fail(HttpStatus.CONFLICT,"教师尚未开始本次课");
        var user=users.lockById(userId).orElseThrow(()->fail(HttpStatus.BAD_REQUEST,"学生不存在"));
        if(!user.isEnabled()||!UserRole.STUDENT.equals(user.getRole())||!roster.member(user,lesson.getClassName()))throw fail(HttpStatus.FORBIDDEN,"学生不属于当前教学班");
        var session=sessions.findByIdAndUserId(sessionId,userId).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"实验记录不属于该学生"));
        if(!"ACTIVE".equals(session.getStatus()))throw fail(HttpStatus.CONFLICT,"实验已经结束");
        var room=rooms.findById(s.getRoomId()).orElseThrow();
        if(!session.getExperimentCode().equals(lessonExperiment(lesson)))throw fail(HttpStatus.CONFLICT,"实验与工位所在实验室不一致");
        if(session.getStudentClass()!=null&&!session.getStudentClass().isBlank()&&!session.getStudentClass().equals(lesson.getClassName()))throw fail(HttpStatus.CONFLICT,"实验记录不属于本次教学班");
        for(var prior:uses.findByUserIdAndEndedAtIsNull(userId)) {
            if(sessions.findById(prior.getSessionId()).map(x->"ACTIVE".equals(x.getStatus())).orElse(false)) {
                if(prior.getStationId().equals(s.getId())&&prior.getSessionId().equals(sessionId))return prior;
                throw fail(HttpStatus.CONFLICT,"学生已在其他工位或实验记录中入座，请先离座");
            }else end(prior,"EXPERIMENT_FINISHED");
        }
        var current=cleanUses(s.getId());
        if(current.isEmpty()){s.setGroupCodeHash(null);s.setGroupCodeExpiresAt(null);s.setGroupLessonId(null);stations.save(s);}
        if(!group&&!current.isEmpty())throw fail(HttpStatus.CONFLICT,"工位已有学生，组员请由教师添加");
        if(current.size()>=s.getCapacity())throw fail(HttpStatus.CONFLICT,"已达到工位人数上限");
        // 旧会话无班级时，仅在学生确认当前课次后补齐。
        if(session.getStudentClass()==null||session.getStudentClass().isBlank()){session.setStudentClass(lesson.getClassName());sessions.save(session);}
        var use=new WorkstationUse();use.setStationId(s.getId());use.setLessonId(lesson.getId());use.setUserId(userId);use.setSessionId(sessionId);
        use.setStudentName(user.getDisplayName());use.setClassName(lesson.getClassName());use.setCameraCode(s.getCameraCode());return uses.save(use);
    }
    @Transactional
    public WorkstationUse addGroupMember(Long teacherId,Long id,Long sessionId) {
        var teacher=teacher(teacherId);var original=station(id);lockRoom(original.getRoomId());var s=lockStation(id);lessonAccess(teacher,activeLesson(s.getRoomId()));
        var session=sessions.findById(sessionId).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"实验记录不存在"));return join(s,session.getUserId(),sessionId,true);
    }
    @Transactional
    public Map<String,Object> mySeat(Long userId) {
        for(var use:uses.findByUserIdAndEndedAtIsNull(userId)) {
            if(cleanUses(use.getStationId()).stream().noneMatch(u->u.getId().equals(use.getId())))continue;
            var lesson=lessons.findById(use.getLessonId()).orElseThrow();
            if(!"ACTIVE".equals(lesson.getStatus())){end(use,"LESSON_CLOSED");continue;}
            var station=station(use.getStationId());
            return Map.of("use",use,"station",Map.of("id",station.getId(),"code",station.getCode(),"name",station.getName()),"room",rooms.findById(station.getRoomId()).orElseThrow(),"lesson",lesson);
        }
        return Map.of();
    }
    @Transactional
    public Map<String,Object> groupCode(Long userId,String token) {
        var original=terminal(token);lockRoom(original.getRoomId());var s=lockStation(original.getId());
        if(!Objects.equals(s.getTerminalHash(),hash(token)))throw fail(HttpStatus.CONFLICT,"终端绑定已失效");
        var lesson=activeLesson(s.getRoomId());
        if(lesson==null||cleanUses(s.getId()).stream().noneMatch(u->u.getUserId().equals(userId)))throw fail(HttpStatus.FORBIDDEN,"请先在本工位确认入座");
        String code=secret().substring(0,10);s.setGroupCodeHash(hash(code));s.setGroupCodeExpiresAt(LocalDateTime.now().plusMinutes(10));s.setGroupLessonId(lesson.getId());stations.save(s);
        return Map.of("code",code,"expiresAt",s.getGroupCodeExpiresAt());
    }
    @Transactional
    public WorkstationUse joinGroup(Long userId,String code,Long sessionId) {
        var original=stations.findByGroupCodeHash(hash(text(code))).orElseThrow(()->fail(HttpStatus.BAD_REQUEST,"入组码无效或已失效"));
        lockRoom(original.getRoomId());var s=lockStation(original.getId());var lesson=activeLesson(s.getRoomId());
        if(lesson==null||!Objects.equals(s.getGroupCodeHash(),hash(text(code)))||!Objects.equals(s.getGroupLessonId(),lesson.getId())||s.getGroupCodeExpiresAt()==null||s.getGroupCodeExpiresAt().isBefore(LocalDateTime.now())||cleanUses(s.getId()).isEmpty())throw fail(HttpStatus.CONFLICT,"入组码已失效，请让组员重新生成");
        return join(s,userId,sessionId,true);
    }
    @Transactional
    public void leaveMySeat(Long userId) {
        for(var use:uses.findByUserIdAndEndedAtIsNull(userId)) {
            var original=station(use.getStationId());lockRoom(original.getRoomId());lockStation(original.getId());end(use,"STUDENT_LEFT");
        }
    }
    @Transactional
    public void release(Long userId,String token) {
        var original=terminal(token);lockRoom(original.getRoomId());var s=lockStation(original.getId());
        if(!Objects.equals(s.getTerminalHash(),hash(token)))throw fail(HttpStatus.CONFLICT,"终端绑定已失效");
        for(var use:uses.findByStationIdAndEndedAtIsNull(s.getId()))if(use.getUserId().equals(userId))end(use,"STUDENT_LEFT");
    }
    @Transactional
    public void releaseByTeacher(Long teacherId,Long id) {
        var teacher=teacher(teacherId);var original=station(id);lockRoom(original.getRoomId());var s=lockStation(id);lessonAccess(teacher,activeLesson(s.getRoomId()));
        for(var use:uses.findByStationIdAndEndedAtIsNull(id))end(use,"TEACHER_RELEASED");s.setInspectionEnabled(false);s.setGroupCodeHash(null);s.setGroupCodeExpiresAt(null);s.setGroupLessonId(null);stations.save(s);
    }
    @Transactional
    public Map<String,Object> publisherKey(Long teacherId,Long id) {
        deviceManager(teacher(teacherId));var s=lockStation(id);
        if(!"USB".equals(s.getCameraType()))throw fail(HttpStatus.BAD_REQUEST,"仅 USB 摄像头需要采集密钥");
        String key=secret();s.setPublisherHash(hash(key));stations.save(s);frames.clear(id);return Map.of("stationId",id,"publisherKey",key);
    }
    @Transactional
    public void inspectionEnabled(Long teacherId,Long id,boolean enabled) {
        var teacher=teacher(teacherId);var s=lockStation(id);lessonAccess(teacher,activeLesson(s.getRoomId()));
        if(enabled&&cleanUses(id).isEmpty())throw fail(HttpStatus.CONFLICT,"空闲工位不执行巡检");
        s.setInspectionEnabled(enabled);stations.save(s);
    }
    @Transactional
    public List<WorkstationUse> history(Long teacherId,Long id) {
        var teacher=teacher(teacherId);station(id);String cls=FeedbackService.managedClass(teacher);
        return uses.findTop100ByStationIdOrderByJoinedAtDesc(id).stream().filter(u->cls.isBlank()||cls.equals(u.getClassName())).toList();
    }
    public List<EnvCheckLog> logs(Long teacherId,Long id) {
        var teacher=teacher(teacherId);station(id);String cls=FeedbackService.managedClass(teacher);
        return logs.findTop20ByWorkstationIdOrderByCreatedAtDesc(id).stream().filter(l->cls.isBlank()||uses.findById(l.getWorkstationUseId()).map(u->cls.equals(u.getClassName())).orElse(false)).toList();
    }
    @Transactional
    public void releaseFinished(Long userId) {
        for(var use:uses.findByUserIdAndEndedAtIsNull(userId)){
            var original=station(use.getStationId());lockRoom(original.getRoomId());lockStation(original.getId());cleanUses(original.getId());
        }
    }
    @Transactional
    public void inspectionOutcome(Long id,String error) {
        var s=lockStation(id);s.setLastInspectionAt(LocalDateTime.now());s.setInspectionError(error);
        if(cleanUses(id).isEmpty())s.setInspectionEnabled(false);stations.save(s);
    }
    public record InspectionPlan(LabWorkstation station,LabRoom room,String experimentCode,List<WorkstationUse> uses,WorkstationFrames.Frame frame){}
    @Transactional
    public InspectionPlan prepareInspection(Long id) {
        var s=lockStation(id);var current=cleanUses(id);
        if(!s.isEnabled()||current.isEmpty())throw fail(HttpStatus.CONFLICT,"工位未启用或无人使用");
        s.setLastInspectionAt(LocalDateTime.now());stations.save(s);
        // 有限时抓拍期间持有工位锁；AI 分析不持锁，允许正常离座和下一次入座。
        var frame=frames.capture(s);
        return new InspectionPlan(s,rooms.findById(s.getRoomId()).orElseThrow(),lessonExperiment(activeLesson(s.getRoomId())),List.copyOf(current),frame);
    }
}
