package com.wuxiaozhi.repository;
import com.wuxiaozhi.entity.StudentClassMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StudentClassMembershipRepository extends JpaRepository<StudentClassMembership,Long> {
    List<StudentClassMembership> findByUserId(Long userId);
    List<StudentClassMembership> findByClassName(String className);
    List<StudentClassMembership> findByUserIdIn(java.util.Collection<Long> userIds);
    boolean existsByUserIdAndClassName(Long userId,String className);
}
