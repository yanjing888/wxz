package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.wuxiaozhi.service.WorkstationService.fail;

@Service
@Transactional(readOnly=true)
public class LearningService {
    private final UserRepository users;
    private final ClassRosterService roster;
    private final StudentExperimentAssignmentRepository assignments;
    private final StudentExperimentProgressRepository progress;
    private final LabSessionRepository sessions;
    private final SessionDataLogRepository data;
    private final LearningReflectionRepository reflections;
    private final ExperimentConfigService experiments;
    private final StudentExperimentAccessService access;
    private final ObjectMapper json;
    public LearningService(UserRepository users,ClassRosterService roster,StudentExperimentAssignmentRepository assignments,StudentExperimentProgressRepository progress,LabSessionRepository sessions,SessionDataLogRepository data,LearningReflectionRepository reflections,ExperimentConfigService experiments,StudentExperimentAccessService access,ObjectMapper json) {
        this.users=users;this.roster=roster;this.assignments=assignments;this.progress=progress;this.sessions=sessions;this.data=data;this.reflections=reflections;this.experiments=experiments;this.access=access;this.json=json;
    }
    public record Dimension(String key,String label,Double value) {}
    public record PointItem(String label,int points) {}
    public record ExperimentItem(String code,String name,String className,LocalDateTime assignedAt,LocalDateTime dueAt,String state,boolean currentTask,boolean prepared,boolean finished,boolean dataRecorded,boolean submitted,boolean graded,Double score,String comment,LocalDateTime gradedAt,LocalDateTime finishedAt,int points,List<PointItem> pointItems,String reflection,LocalDateTime reflectionAt,boolean reflectionCurrent,String feedbackRevision) {}
    public record Summary(Long userId,String name,String username,int totalPoints,int assignedCount,int finishedCount,int submittedCount,int gradedCount,Double averageScore,List<Dimension> dimensions,List<ExperimentItem> records) {}
    public record Ranking(Integer rank,Long userId,String name,String username,int points,int finishedCount,int gradedCount,Double averageScore,boolean self) {}
    private record Snapshot(List<StudentExperimentAssignment> assignments,List<StudentExperimentProgress> progress,List<LabSession> sessions,List<SessionDataLog> data,List<LearningReflection> reflections) {}

    private User require(Long id,String... roles) {
        var user=users.findById(id).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"无权访问"));
        if(!user.isEnabled()||!List.of(roles).contains(user.getRole()))throw fail(HttpStatus.FORBIDDEN,"无权访问");return user;
    }
    private Snapshot snapshot(List<User> students) {
        var ids=students.stream().map(User::getId).toList();
        if(ids.isEmpty())return new Snapshot(List.of(),List.of(),List.of(),List.of(),List.of());
        var lab=sessions.findByUserIdIn(ids);
        return new Snapshot(assignments.findByUserIdIn(ids),progress.findByUserIdIn(ids),lab,lab.isEmpty()?List.of():data.findBySessionIdIn(lab.stream().map(LabSession::getId).toList()),reflections.findByUserIdIn(ids));
    }
    private String selectedClass(User viewer,String requested,boolean student) {
        var visible=student?new ArrayList<>(roster.classes(viewer)):roster.visibleClasses(viewer);
        String name=requested==null||requested.isBlank()?(visible.isEmpty()?"":visible.get(0)):requested.trim();
        if(!name.isEmpty()&&!visible.contains(name))throw fail(HttpStatus.FORBIDDEN,"无权查看该教学班");return name;
    }
    public Map<String,Object> student(Long id,String className) {
        var user=require(id,UserRole.STUDENT);String selected=selectedClass(user,className,true);
        var classmates=selected.isEmpty()?List.of(user):roster.studentsInClass(selected).stream().filter(User::isEnabled).toList();
        var cohort=snapshot(classmates);
        var summaries=classmates.stream().map(s->summarize(s,selected,cohort)).toList();
        var all=snapshot(List.of(user));
        var result=new LinkedHashMap<String,Object>();result.put("summary",summarize(user,null,all));result.put("classes",roster.classes(user));result.put("className",selected);
        var board=ranking(summaries,id,false);result.put("leaderboard",board);result.put("selfRank",board.stream().filter(Ranking::self).map(r->r.rank()==null?0:r.rank()).findFirst().orElse(0));return result;
    }
    public Map<String,Object> teacher(Long id,String className) {
        return teacher(id,className,null);
    }
    public Map<String,Object> teacher(Long id,String className,String experimentCode) {
        var teacher=require(id,UserRole.TEACHER,UserRole.ADMIN);String selected=selectedClass(teacher,className,false);
        String code=experimentFilter(experimentCode);
        var candidates=selected.isEmpty()?List.<User>of():roster.students(teacher,selected).stream().filter(User::isEnabled).toList();var cohort=snapshot(candidates);
        var students=code==null?candidates:candidates.stream().filter(s->cohort.assignments().stream().anyMatch(a->a.getUserId().equals(s.getId())&&a.getExperimentCode().equals(code)&&assignmentClass(a,s).equals(selected))).toList();
        var summaries=students.stream().map(s->summarize(s,selected,cohort,code)).toList();
        var result=new LinkedHashMap<String,Object>();result.put("classes",roster.visibleClasses(teacher));result.put("className",selected);result.put("studentCount",students.size());
        result.put("activeCount",summaries.stream().filter(s->s.totalPoints()>0).count());result.put("averagePoints",average(summaries.stream().map(s->(double)s.totalPoints()).toList()));
        result.put("dimensions",averageDimensions(summaries));result.put("leaderboard",ranking(summaries,null,true));return result;
    }
    public Summary teacherStudent(Long teacherId,Long studentId,String className) {
        return teacherStudent(teacherId,studentId,className,null);
    }
    public Summary teacherStudent(Long teacherId,Long studentId,String className,String experimentCode) {
        var teacher=require(teacherId,UserRole.TEACHER,UserRole.ADMIN);String selected=selectedClass(teacher,className,false);
        var student=require(studentId,UserRole.STUDENT);
        if(selected.isBlank()||!roster.member(student,selected))throw fail(HttpStatus.FORBIDDEN,"无权查看该学生");
        return summarize(student,selected,snapshot(List.of(student)),experimentFilter(experimentCode));
    }
    private Summary summarize(User student,String className,Snapshot snapshot) {
        return summarize(student,className,snapshot,null);
    }
    private String experimentFilter(String value) {
        if(value==null||value.isBlank())return null;String code=value.trim();
        if(experiments.listAll().stream().noneMatch(e->e.getCode().equals(code)))throw fail(HttpStatus.BAD_REQUEST,"实验项目不存在");return code;
    }
    private Summary summarize(User student,String className,Snapshot snapshot,String experimentCode) {
        var allAssigned=snapshot.assignments().stream().filter(a->a.getUserId().equals(student.getId())).sorted(Comparator.comparing(StudentExperimentAssignment::getAssignedAt).thenComparing(StudentExperimentAssignment::getId)).toList();
        var assigned=allAssigned.stream().filter(a->(className==null||className.equals(assignmentClass(a,student)))&&(experimentCode==null||experimentCode.equals(a.getExperimentCode()))).toList();
        var history=snapshot.sessions().stream().filter(s->s.getUserId().equals(student.getId())).sorted(Comparator.comparing(LabSession::getStartTime).reversed()).toList();
        var finishedCodes=history.stream().filter(s->"FINISHED".equals(s.getStatus())).map(LabSession::getExperimentCode).collect(Collectors.toSet());
        var pending=allAssigned.stream().filter(a->!finishedCodes.contains(a.getExperimentCode())).toList();
        String currentCode=history.stream().filter(s->"ACTIVE".equals(s.getStatus())&&pending.stream().anyMatch(a->a.getExperimentCode().equals(s.getExperimentCode()))).map(LabSession::getExperimentCode).findFirst().orElse(pending.isEmpty()?"":pending.get(0).getExperimentCode());
        var configs=experiments.listAll().stream().collect(Collectors.toMap(c->c.getCode(),Function.identity()));
        var records=new ArrayList<ExperimentItem>();var quality=new ArrayList<Double>();
        for(var assignment:assigned) {
            String code=assignment.getExperimentCode();var config=configs.get(code);
            var p=snapshot.progress().stream().filter(v->v.getUserId().equals(student.getId())&&v.getExperimentCode().equals(code)).findFirst().orElse(null);
            var lab=snapshot.sessions().stream().filter(s->s.getUserId().equals(student.getId())&&s.getExperimentCode().equals(code)&&(className==null||className.equals(sessionClass(s,assignment,student)))).sorted(Comparator.comparing(LabSession::getStartTime).reversed()).toList();
            var finished=lab.stream().filter(s->"FINISHED".equals(s.getStatus())).findFirst().orElse(null);
            // Prefer the session attached to the submitted report when one exists.
            if(p!=null&&p.getReportSessionId()!=null)finished=lab.stream().filter(s->s.getId().equals(p.getReportSessionId())&&"FINISHED".equals(s.getStatus())).findFirst().orElse(finished);
            boolean reportInScope=p!=null&&(p.getReportSessionId()==null||lab.stream().anyMatch(s->s.getId().equals(p.getReportSessionId())&&"FINISHED".equals(s.getStatus())));
            boolean done=finished!=null,prepared=p!=null&&(p.isSimulationCompleted()||p.isPreLabCompleted()),submitted=done&&p!=null&&p.isReportCompleted()&&reportInScope;
            boolean graded=submitted&&Boolean.TRUE.equals(p.getGradingCompleted());
            Double score=graded?bounded(p.teacherScoreAsPercent()):null;
            var reflection=snapshot.reflections().stream().filter(r->r.getUserId().equals(student.getId())&&r.getExperimentCode().equals(code)).findFirst().orElse(null);
            boolean reflected=graded&&reflection!=null&&feedbackRevision(p).equals(reflection.getFeedbackRevision());
            var sessionIds=lab.stream().map(LabSession::getId).collect(Collectors.toSet());
            var official=snapshot.data().stream().filter(d->sessionIds.contains(d.getSessionId())&&!Boolean.FALSE.equals(d.getOfficialData())).toList();
            boolean recorded=!official.isEmpty();
            // Only the latest official measurement for each step is used for data quality.
            var latest=new HashMap<String,SessionDataLog>();
            for(var log:official){String key=String.valueOf(log.getStepId());var previous=latest.get(key);if(previous==null||log.getId()>previous.getId())latest.put(key,log);}
            for(var log:latest.values()) {var value=validation(log);if(value!=null)quality.add(value);}
            var earned=new ArrayList<PointItem>();
            if(prepared)earned.add(new PointItem("实验准备",10));
            if(done)earned.add(new PointItem("完成实验",20));
            if(recorded)earned.add(new PointItem("记录数据",10));
            if(submitted)earned.add(new PointItem("提交报告",20));
            if(reflected)earned.add(new PointItem("反馈订正",10));
            if(score!=null)earned.add(new PointItem("教师评分",(int)Math.round(score*0.3)));
            int points=earned.stream().mapToInt(PointItem::points).sum();
            boolean current=!done&&code.equals(currentCode);
            String state=graded?"GRADED":submitted?"SUBMITTED":done?"REPORT_PENDING":current?"CURRENT":"QUEUED";
            records.add(new ExperimentItem(code,config==null?code:config.getName(),assignmentClass(assignment,student),assignment.getAssignedAt(),assignment.getReportDueAt(),state,current,prepared,done,recorded,submitted,graded,score,graded?p.getTeacherComment():null,graded?p.getGradingAt():null,finished==null?null:finished.getEndTime(),points,earned,reflection==null?null:reflection.getContent(),reflection==null?null:reflection.getSubmittedAt(),reflected,graded?feedbackRevision(p):""));
        }
        int count=records.size(),finished=(int)records.stream().filter(ExperimentItem::finished).count(),submitted=(int)records.stream().filter(ExperimentItem::submitted).count(),graded=(int)records.stream().filter(ExperimentItem::graded).count();
        var dimensions=List.of(new Dimension("preparation","实验准备",ratio(records.stream().filter(ExperimentItem::prepared).count(),count)),new Dimension("execution","实验完成",ratio(finished,count)),new Dimension("data","数据质量",average(quality)),new Dimension("report","报告质量",average(records.stream().map(ExperimentItem::score).filter(Objects::nonNull).toList())),new Dimension("reflection","反馈订正",ratio(records.stream().filter(ExperimentItem::reflectionCurrent).count(),graded)));
        return new Summary(student.getId(),student.getDisplayName(),student.getUsername(),records.stream().mapToInt(ExperimentItem::points).sum(),count,finished,submitted,graded,average(records.stream().map(ExperimentItem::score).filter(Objects::nonNull).toList()),dimensions,records);
    }
    private Double validation(SessionDataLog log) {
        if(log.getValidationJson()==null||log.getValidationJson().isBlank())return null;
        try {var ok=json.readTree(log.getValidationJson()).get("ok");return ok!=null&&ok.isBoolean()?(ok.asBoolean()?100.0:0.0):null;}catch(Exception ignored){return null;}
    }
    private static Double bounded(Double value){return value==null?null:Math.max(0,Math.min(100,value));}
    private static String assignmentClass(StudentExperimentAssignment assignment,User student){return ClassRosterService.classKey(assignment.getClassName()==null||assignment.getClassName().isBlank()?student.getStudentClass():assignment.getClassName());}
    private String sessionClass(LabSession session,StudentExperimentAssignment assignment,User student) {
        String saved=ClassRosterService.classKey(session.getStudentClass());if(!"未分班".equals(saved))return saved;
        if(assignment.getClassName()!=null&&!assignment.getClassName().isBlank()&&!"未分班".equals(assignment.getClassName()))return assignment.getClassName();
        var classes=roster.classes(student);return classes.size()==1?classes.iterator().next():saved;
    }
    private static Double ratio(long numerator,long denominator){return denominator==0?null:Math.round(1000.0*numerator/denominator)/10.0;}
    private static Double average(List<Double> values){return values.isEmpty()?null:Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()*10)/10.0;}
    private List<Dimension> averageDimensions(List<Summary> students) {
        var keys=List.of("preparation","execution","data","report","reflection");var labels=List.of("实验准备","实验完成","数据质量","报告质量","反馈订正");var result=new ArrayList<Dimension>();
        for(int i=0;i<keys.size();i++){String key=keys.get(i);result.add(new Dimension(key,labels.get(i),average(students.stream().flatMap(s->s.dimensions().stream()).filter(d->d.key().equals(key)&&d.value()!=null).map(Dimension::value).toList())));}return result;
    }
    private List<Ranking> ranking(List<Summary> students,Long selfId,boolean teacher) {
        var sorted=students.stream().sorted(Comparator.comparingInt(Summary::totalPoints).reversed().thenComparing(Summary::username)).toList();var rows=new ArrayList<Ranking>();int rank=0,last=-1;
        for(int i=0;i<sorted.size();i++){var s=sorted.get(i);if(s.totalPoints()!=last)rank=i+1;last=s.totalPoints();boolean self=Objects.equals(s.userId(),selfId);rows.add(new Ranking(s.totalPoints()==0?null:rank,teacher||self?s.userId():null,s.name(),teacher?s.username():mask(s.username()),s.totalPoints(),s.finishedCount(),s.gradedCount(),s.averageScore(),self));}return rows;
    }
    private static String mask(String username){return username.length()<5?"***":username.substring(0,2)+"***"+username.substring(username.length()-2);}
    private static String feedbackRevision(StudentExperimentProgress p){return WorkstationService.hash(String.valueOf(p.getGradingAt())+":"+p.teacherScoreAsPercent()+":"+p.getTeacherComment());}
    @Transactional
    public void reflect(Long userId,String code,String content,String expectedRevision) {
        require(userId,UserRole.STUDENT);
        if(!access.isAssigned(userId,code))throw fail(HttpStatus.FORBIDDEN,"该实验未分配给您");
        var p=progress.lockByUserAndCode(userId,code).orElseThrow(()->fail(HttpStatus.CONFLICT,"报告尚未批阅"));
        if(!p.isReportCompleted()||!Boolean.TRUE.equals(p.getGradingCompleted()))throw fail(HttpStatus.CONFLICT,"报告尚未批阅");
        if(!feedbackRevision(p).equals(expectedRevision))throw fail(HttpStatus.CONFLICT,"教师反馈已更新，请刷新后重新订正");
        var reflection=reflections.findByUserIdAndExperimentCode(userId,code).orElseGet(LearningReflection::new);
        reflection.setUserId(userId);reflection.setExperimentCode(code);reflection.setContent(content.trim());reflection.setFeedbackRevision(expectedRevision);reflection.setSubmittedAt(LocalDateTime.now());reflections.save(reflection);
    }
}
