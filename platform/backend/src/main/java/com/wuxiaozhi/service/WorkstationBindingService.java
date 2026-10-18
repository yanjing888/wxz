package com.wuxiaozhi.service;

import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.wuxiaozhi.service.WorkstationService.fail;

@Service
public class WorkstationBindingService {
    private final WorkstationService workstations;
    private final WorkstationBindingRepository bindings;
    private final LabRoomRepository rooms;
    private final UserRepository users;
    private final ClassRosterService roster;
    public WorkstationBindingService(WorkstationService workstations,WorkstationBindingRepository bindings,LabRoomRepository rooms,UserRepository users,ClassRosterService roster) {
        this.workstations=workstations;this.bindings=bindings;this.rooms=rooms;this.users=users;this.roster=roster;
    }
    private boolean accessible(User teacher,WorkstationBinding binding) {
        return roster.visibleClasses(teacher).contains(binding.getClassName());
    }
    public Map<String,Object> candidates(Long teacherId,Long roomId) {
        var teacher=workstations.teacher(teacherId);
        if(!rooms.existsById(roomId))throw fail(HttpStatus.NOT_FOUND,"实验室不存在");
        var assigned=new HashMap<Long,Long>();bindings.findByRoomId(roomId).forEach(b->assigned.put(b.getUserId(),b.getStationId()));
        var students=roster.students(teacher,"").stream().filter(User::isEnabled).map(u->{
            var row=new LinkedHashMap<String,Object>();row.put("id",u.getId());row.put("name",u.getDisplayName());row.put("username",u.getUsername());
            row.put("classes",roster.classes(u));row.put("stationId",assigned.get(u.getId()));return row;
        }).toList();
        return Map.of("classes",roster.visibleClasses(teacher),"students",students);
    }
    public Map<String,Object> overview(Long teacherId) {
        var teacher=workstations.teacher(teacherId);
        var result=new LinkedHashMap<>(workstations.overview(teacherId));
        var visibleClasses=roster.visibleClasses(teacher);
        var allBindings=bindings.findAll();
        var names=new HashMap<Long,User>();users.findAllById(allBindings.stream().map(WorkstationBinding::getUserId).toList()).forEach(u->names.put(u.getId(),u));
        @SuppressWarnings("unchecked") var stations=(List<Map<String,Object>>)result.get("stations");
        for(var station:stations) {
            var current=allBindings.stream().filter(b->b.getStationId().equals(station.get("id"))).toList();
            station.put("canBind",current.stream().allMatch(b->visibleClasses.contains(b.getClassName())));
            station.put("bindingCount",current.size());
            station.put("members",current.stream().filter(b->visibleClasses.contains(b.getClassName())).map(b->{
                var u=names.get(b.getUserId());return Map.of("id",b.getUserId(),"name",u==null?"已移除学生":u.getDisplayName(),"username",u==null?"":u.getUsername(),"className",b.getClassName());
            }).toList());
        }
        return result;
    }
    @Transactional
    public void save(Long teacherId,Long stationId,String className,List<Long> studentIds) {
        var teacher=workstations.teacher(teacherId);var station=workstations.station(stationId);
        rooms.lockById(station.getRoomId()).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"实验室不存在"));
        var current=bindings.findByStationId(stationId);
        if(current.stream().anyMatch(b->!accessible(teacher,b)))throw fail(HttpStatus.FORBIDDEN,"该工位绑定了其他教学班，请由对应教师清空后再绑定");
        var ids=new LinkedHashSet<>(studentIds);
        if(ids.size()!=studentIds.size()||ids.size()>8)throw fail(HttpStatus.BAD_REQUEST,"每个工位最多选择 8 名不同学生");
        if(!ids.isEmpty()) {
            if(!station.isEnabled())throw fail(HttpStatus.CONFLICT,"该工位已停用");
            if(!roster.visibleClasses(teacher).contains(className))throw fail(HttpStatus.FORBIDDEN,"请选择可管理的教学班");
            var allowed=roster.students(teacher,className).stream().filter(User::isEnabled).map(User::getId).toList();
            if(!allowed.containsAll(ids))throw fail(HttpStatus.BAD_REQUEST,"请选择当前教学班中启用的学生");
            if(bindings.findByRoomId(station.getRoomId()).stream().anyMatch(b->ids.contains(b.getUserId())&&!b.getStationId().equals(stationId)))throw fail(HttpStatus.CONFLICT,"所选学生已绑定其他工位，请先在原工位解除绑定");
        }
        bindings.deleteAll(current);bindings.flush();
        for(var id:ids){var binding=new WorkstationBinding();binding.setRoomId(station.getRoomId());binding.setStationId(stationId);binding.setUserId(id);binding.setClassName(className);binding.setTeacherId(teacherId);bindings.save(binding);}
    }
    @Transactional
    public void clearRoom(Long teacherId,Long roomId) {
        var teacher=workstations.teacher(teacherId);
        rooms.lockById(roomId).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"实验室不存在"));
        var current=bindings.findByRoomId(roomId);
        if(current.stream().anyMatch(b->!accessible(teacher,b)))throw fail(HttpStatus.FORBIDDEN,"实验室含其他教学班的绑定，请由对应教师解除，或逐个管理本班工位");
        bindings.deleteAll(current);
    }
}
