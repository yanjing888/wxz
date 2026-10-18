package com.wuxiaozhi.controller;
import com.wuxiaozhi.dto.AdminUserRequest;
import com.wuxiaozhi.entity.AdminAuditLog;
import com.wuxiaozhi.security.AuthSupport;
import com.wuxiaozhi.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service){this.service=service;}
    public record PasswordRequest(@NotBlank @Size(max=128) String password){}
    @GetMapping("/users") public Map<String,Object> users(@RequestParam(required=false) String query,@RequestParam(required=false) String role,@RequestParam(required=false) String className,Authentication a){return service.list(AuthSupport.currentUserId(a),query,role,className);}
    @PostMapping("/users") public Map<String,Object> create(@Valid @RequestBody AdminUserRequest req,Authentication a){return service.save(AuthSupport.currentUserId(a),null,req);}
    @PutMapping("/users/{id}") public Map<String,Object> update(@PathVariable Long id,@Valid @RequestBody AdminUserRequest req,Authentication a){return service.save(AuthSupport.currentUserId(a),id,req);}
    @PatchMapping("/users/{id}/password") public void password(@PathVariable Long id,@Valid @RequestBody PasswordRequest req,Authentication a){service.resetPassword(AuthSupport.currentUserId(a),id,req.password());}
    @GetMapping("/audit") public List<AdminAuditLog> audit(Authentication a){return service.audit(AuthSupport.currentUserId(a));}
}
