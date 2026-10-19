package com.wuxiaozhi.repository;

import com.wuxiaozhi.entity.StoredUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredUploadRepository extends JpaRepository<StoredUpload, String> {
    @Query("select f.sizeBytes from StoredUpload f where f.filename = :filename")
    Optional<Long> findSizeByFilename(@Param("filename") String filename);
}
