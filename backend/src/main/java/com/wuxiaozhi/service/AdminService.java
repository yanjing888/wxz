package com.wuxiaozhi.service;
import com.wuxiaozhi.dto.AdminUserRequest;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.*;
import static com.wuxiaozhi.service.WorkstationService.fail;

@Service
public class AdminService {
    private final UserRepository users;
    private final StudentClassMembershipRepository memberships;
    private final ClassRosterService roster;
    private final PasswordEncoder passwords;
    private final AdminAuditLogRepository audit;
    private final WorkstationUseRepository uses;
    public AdminService(UserRepository u,StudentClassMembershipRepository m,ClassRosterService r,PasswordEncoder p,AdminAuditLogRepository a,WorkstationUseRepository uses){users=u;memberships=m;roster=r;passwords=p;audit=a;this.uses=uses;}
    public User requireAdmin(Long id){var u=users.findById(id).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"需要管理员权限"));if(!u.isEnabled()||!UserRole.ADMIN.equals(u.getRole()))throw fail(HttpStatus.FORBIDDEN,"需要管理员权限");return u;}
    private Map<String,Object> view(User u) {
        var row=new LinkedHashMap<String,Object>();row.put("id",u.getId());row.put("username",u.getUsername());row.put("displayName",u.getDisplayName());row.put("role",u.getRole());row.put("enabled",u.isEnabled());row.put("studentClass",Objects.toString(u.getStudentClass(),""));row.put("classes",UserRole.STUDENT.equals(u.getRole())?roster.classes(u).stream().filter(c->!"未分班".equals(c)).toList():List.of());row.put("createdAt",u.getCreatedAt());return row;
    }
    public Map<String,Object> list(Long actor,String query,String role,String className) {
        requireAdmin(actor);String q=Objects.toString(query,"").trim().toLowerCase(Locale.ROOT),cls=Objects.toString(className,"").trim();
        var all=users.findAll();var rows=all.stream().filter(u->role==null||role.isBlank()||role.equals(u.getRole())).filter(u->q.isBlank()||(u.getUsername()+" "+u.getDisplayName()).toLowerCase(Locale.ROOT).contains(q)).filter(u->cls.isBlank()||UserRole.STUDENT.equals(u.getRole())&&roster.member(u,cls)).sorted(Comparator.comparing(User::getRole).thenComparing(User::getUsername)).map(this::view).toList();
        return Map.of("users",rows,"classes",all.stream().filter(u->UserRole.STUDENT.equals(u.getRole())).flatMap(u->roster.classes(u).stream()).filter(c->!"未分班".equals(c)).distinct().sorted().toList());
    }
    @Transactional
    public Map<String,Object> save(Long actor,Long id,AdminUserRequest req) {
        requireAdmin(actor);var admins=users.lockAdmins();
        var u=id==null?new User():users.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"账号不存在"));
        if(id==null&&users.existsByUsername(req.username().trim()))throw fail(HttpStatus.CONFLICT,"账号已存在");
        if(id!=null&&!u.getUsername().equals(req.username().trim()))throw fail(HttpStatus.BAD_REQUEST,"账号不可修改，请修改姓名或其他信息");
        if(id!=null&&UserRole.ADMIN.equals(u.getRole())&&(!req.enabled()||!UserRole.ADMIN.equals(req.role()))) {
            if(actor.equals(id))throw fail(HttpStatus.CONFLICT,"不能停用自己的管理员账号或移除自己的管理员身份");
            if(admins.stream().filter(User::isEnabled).count()<=1)throw fail(HttpStatus.CONFLICT,"至少保留一个启用的管理员");
        }
        if(id==null&&(req.password()==null||req.password().isBlank()))throw fail(HttpStatus.BAD_REQUEST,"创建账号时请填写初始密码");
        String cls=Objects.toString(req.studentClass(),"").trim();
        if(!UserRole.STUDENT.equals(req.role())&&req.classes()!=null&&!req.classes().isEmpty())throw fail(HttpStatus.BAD_REQUEST,"只有学生账号可加入多个教学班");
        if(id!=null&&!Objects.equals(u.getRole(),req.role())&&(!uses.findByUserIdAndEndedAtIsNull(id).isEmpty()))throw fail(HttpStatus.CONFLICT,"账号正在工位使用中，请先释放工位再变更身份");
        u.setUsername(req.username().trim());u.setDisplayName(req.displayName().trim());u.setRole(req.role());u.setEnabled(req.enabled());u.setStudentClass(UserRole.ADMIN.equals(req.role())?"":cls);
        if(req.password()!=null&&!req.password().isBlank())u.setPasswordHash(passwords.encode(req.password()));
        if(id!=null)u.setAuthVersion(u.getAuthVersion()+1);
        users.save(u);
        var desired=new LinkedHashSet<String>();if(UserRole.STUDENT.equals(req.role())){if(!cls.isBlank())desired.add(cls);if(req.classes()!=null)req.classes().forEach(c->desired.add(c.trim()));}
        for(var member:memberships.findByUserId(u.getId()))if(!desired.contains(member.getClassName()))memberships.delete(member);
        for(var name:desired)if(!memberships.existsByUserIdAndClassName(u.getId(),name)){var m=new StudentClassMembership();m.setUserId(u.getId());m.setClassName(name);m.setAddedByUserId(actor);memberships.save(m);}
        if(!u.isEnabled())for(var use:uses.findByUserIdAndEndedAtIsNull(u.getId())){use.setEndedAt(LocalDateTime.now());use.setEndReason("ACCOUNT_DISABLED");uses.save(use);}
        record(actor,id==null?"CREATE_USER":"UPDATE_USER",u.getId(),u.getUsername()+" · "+u.getRole()+" · "+(u.isEnabled()?"启用":"停用"));return view(u);
    }
    @Transactional
    public void resetPassword(Long actor,Long id,String password) {
        requireAdmin(actor);var u=users.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"账号不存在"));
        if(password==null||password.isBlank()||password.length()>128)throw fail(HttpStatus.BAD_REQUEST,"请填写有效的新密码");
        u.setPasswordHash(passwords.encode(password));u.setAuthVersion(u.getAuthVersion()+1);users.save(u);record(actor,"RESET_PASSWORD",id,u.getUsername()+" · 重置密码");
    }
    private void record(Long actor,String action,Long target,String summary){var log=new AdminAuditLog();log.setActorId(actor);log.setAction(action);log.setTargetUserId(target);log.setSummary(summary);audit.save(log);}
    public List<AdminAuditLog> audit(Long actor){requireAdmin(actor);return audit.findTop100ByOrderByCreatedAtDesc();}
}
