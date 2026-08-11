package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findFirstByDisplayName(String displayName);
    boolean existsByUsername(String username);

    List<User> findByRoleOrderByDisplayNameAsc(String role);

    List<User> findByRoleAndStudentClassOrderByDisplayNameAsc(String role, String studentClass);

    long countByRole(String role);

    long countByRoleAndStudentClass(String role, String studentClass);
}
