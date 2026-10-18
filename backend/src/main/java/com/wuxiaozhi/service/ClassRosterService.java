package com.wuxiaozhi.service;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class ClassRosterService {
    private final StudentClassMembershipRepository memberships;
    private final UserRepository users;
    public ClassRosterService(StudentClassMembershipRepository memberships,UserRepository users){this.memberships=memberships;this.users=users;}
    public static String classKey(String value){return value==null||value.isBlank()?"未分班":value.trim();}
    public Set<String> classes(User student){
        Set<String> result=new LinkedHashSet<>();
        result.add(classKey(student.getStudentClass()));
        memberships.findByUserId(student.getId()).forEach(m->result.add(m.getClassName()));
        if(result.size()>1)result.remove("未分班");
        return result;
    }
    public boolean member(User student,String className){return classes(student).contains(classKey(className));}
    public String scope(User teacher,String requested){
        String managed=FeedbackService.managedClass(teacher);
        String value=requested==null?"":requested.trim();
        if(!managed.isBlank()&&!value.isBlank()&&!managed.equals(value))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权管理该教学班");
        return managed.isBlank()?value:managed;
    }
    public List<User> students(User teacher,String requested){
        String scope=scope(teacher,requested);
        if(scope.isBlank())return users.findByRoleOrderByDisplayNameAsc(UserRole.STUDENT);
        return studentsInClass(scope);
    }
    /** Caller must authorize access to this class before listing its roster. */
    public List<User> studentsInClass(String scope){
        if("未分班".equals(scope))return users.findByRoleOrderByDisplayNameAsc(UserRole.STUDENT).stream().filter(u->classes(u).contains(scope)).toList();
        Map<Long,User> result=new LinkedHashMap<>();
        users.findByRoleAndStudentClassOrderByDisplayNameAsc(UserRole.STUDENT,scope).forEach(u->result.put(u.getId(),u));
        var ids=memberships.findByClassName(scope).stream().map(StudentClassMembership::getUserId).toList();
        users.findAllById(ids).stream().filter(u->UserRole.STUDENT.equals(u.getRole())).forEach(u->result.put(u.getId(),u));
        return new ArrayList<>(result.values());
    }
    public Map<Long,Set<String>> classMap(List<User> students){
        Map<Long,Set<String>> result=new LinkedHashMap<>();
        students.forEach(u->result.put(u.getId(),new LinkedHashSet<>(List.of(classKey(u.getStudentClass())))));
        if(!result.isEmpty())memberships.findByUserIdIn(result.keySet()).forEach(m->result.get(m.getUserId()).add(m.getClassName()));
        result.values().forEach(set->{if(set.size()>1)set.remove("未分班");});return result;
    }
    public List<String> visibleClasses(User teacher){
        String managed=FeedbackService.managedClass(teacher);
        if(!managed.isBlank())return List.of(managed);
        return classMap(users.findByRoleOrderByDisplayNameAsc(UserRole.STUDENT)).values().stream().flatMap(Set::stream).distinct().sorted().toList();
    }
    @Transactional
    public void join(User teacher,User student,String className){
        String name=classKey(scope(teacher,className));
        if(!memberships.existsByUserIdAndClassName(student.getId(),name)){
            StudentClassMembership m=new StudentClassMembership();m.setUserId(student.getId());m.setClassName(name);m.setAddedByUserId(teacher.getId());memberships.save(m);
        }
    }
}
