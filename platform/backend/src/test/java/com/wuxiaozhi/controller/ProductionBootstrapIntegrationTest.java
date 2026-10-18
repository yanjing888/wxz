package com.wuxiaozhi.controller;

import com.wuxiaozhi.support.MySqlIntegrationTestSupport;

import com.wuxiaozhi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "wuxiaozhi.test.database-id=production-bootstrap",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wuxiaozhi.auth.demo-users-enabled=false",
        "WXZ_ADMIN_USERNAME=release-admin",
        "WXZ_ADMIN_PASSWORD=release-unit-password",
        "wuxiaozhi.jwt.secret=production-bootstrap-test-secret-long-enough-for-hs256"
})
class ProductionBootstrapIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired UserRepository users;

    @Test
    void initializesAdministratorWithoutCreatingPublicDemoAccounts() {
        assertThat(users.findByUsername("release-admin")).isPresent();
        assertThat(users.findByUsername("test01")).isEmpty();
        assertThat(users.findByUsername("teacher01")).isEmpty();
    }
}
