package com.wuxiaozhi.config;

import com.wuxiaozhi.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyUploadMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyUploadMigration.class);
    private final FileStorageService files;

    public LegacyUploadMigration(FileStorageService files) { this.files = files; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int imported = files.importLegacyUploads();
        log.info("Upload database migration complete: {} files imported; new uploads use MySQL. Legacy originals retained.", imported);
    }
}
