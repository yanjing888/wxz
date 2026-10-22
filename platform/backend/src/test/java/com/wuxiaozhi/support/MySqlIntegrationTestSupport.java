package com.wuxiaozhi.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

/** Each integration-test context owns a fresh MySQL database, never the application database. */
@ContextConfiguration(initializers = MySqlIntegrationTestSupport.DatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MySqlIntegrationTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    private com.wuxiaozhi.repository.LabSessionRepository fixtureSessions;
    @org.springframework.beans.factory.annotation.Autowired
    private com.wuxiaozhi.repository.StepAssessmentRepository fixtureAssessments;
    @org.springframework.beans.factory.annotation.Autowired
    private com.wuxiaozhi.service.ExperimentConfigService fixtureExperiments;

    /** Unrelated workflow tests need a completed-process fixture before ending an experiment.
     * The process-specific integration test exercises completion and review through real APIs. */
    protected void completeProcessFixture(Long sessionId) {
        var session = fixtureSessions.findById(sessionId).orElseThrow();
        fixtureExperiments.getByCode(session.getExperimentCode()).getSteps().forEach((id, config) -> {
            int step = Integer.parseInt(id);
            var row = fixtureAssessments.findByUserIdAndExperimentCodeAndStepId(session.getUserId(), session.getExperimentCode(), step)
                    .orElseGet(com.wuxiaozhi.entity.StepAssessment::new);
            row.setSessionId(sessionId); row.setUserId(session.getUserId()); row.setExperimentCode(session.getExperimentCode());
            row.setStepId(step); row.setStatus(Boolean.FALSE.equals(config.getAssessmentRequired()) ? "COMPLETED" : "PASSED");
            row.setSubmittedAt(java.time.LocalDateTime.now());
            row.setEvidenceJson("{\"images\":[\"/uploads/test-fixture.png\"],\"table\":[[\"Reading\"],[\"1\"]]}");
            row.setScore(config.getAssessmentMaxScore() == null ? 10.0 : config.getAssessmentMaxScore());
            fixtureAssessments.saveAndFlush(row);
        });
    }
    public static class DatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                for (var source : new org.springframework.boot.env.YamlPropertySourceLoader().load("test-defaults",
                        new org.springframework.core.io.FileSystemResource("../config/application.yml.example"))) {
                    context.getEnvironment().getPropertySources().addLast(source);
                }
                // Tests must never import real students' disk uploads. A test
                // that exercises migration supplies its own higher-priority path.
                var defaults = new org.springframework.core.env.MapPropertySource("test-upload-defaults",
                        java.util.Map.of("wuxiaozhi.upload.dir", "target/test-legacy-uploads-" + UUID.randomUUID()));
                context.getEnvironment().getPropertySources().addBefore("test-defaults", defaults);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Missing platform/config/application.yml.example for integration tests", error);
            }
            String server = setting("WXZ_TEST_MYSQL_SERVER", "jdbc:mysql://127.0.0.1:3306/");
            String user = setting("WXZ_TEST_MYSQL_USER", null);
            String password = setting("WXZ_TEST_MYSQL_PASSWORD", null);
            if (user == null || user.isBlank() || password == null) {
                throw new IllegalStateException("Set WXZ_TEST_MYSQL_USER and WXZ_TEST_MYSQL_PASSWORD for an account allowed to create/drop isolated test databases. See platform/README.md.");
            }
            if (!server.matches("jdbc:mysql://[^/]+/")) {
                throw new IllegalStateException("WXZ_TEST_MYSQL_SERVER must be jdbc:mysql://host:port/ without a database name.");
            }
            String label = context.getEnvironment().getProperty("wuxiaozhi.test.database-id", "integration")
                    .replaceAll("[^A-Za-z0-9_]", "_");
            String database = "wxz_test_" + label.substring(0, Math.min(label.length(), 24)) + "_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String options = "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
            execute(server + "mysql" + options, user, password, "CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            DefaultSingletonBeanRegistry registry = (DefaultSingletonBeanRegistry) context.getBeanFactory();
            String cleanup = "isolatedMySqlDatabaseCleanup";
            registry.registerSingleton(cleanup, new Object());
            registry.registerDisposableBean(cleanup,
                    () -> execute(server + "mysql" + options, user, password, "DROP DATABASE IF EXISTS `" + database + "`"));
            // Close JPA and its pool before removing the test database.
            registry.registerDependentBean(cleanup, "dataSource");
            TestPropertyValues.of(
                    "spring.datasource.url=" + server + database + options,
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.datasource.username=" + user,
                    "spring.datasource.password=" + password,
                    "spring.datasource.hikari.maximum-pool-size=3"
            ).applyTo(context.getEnvironment());
        }

        private static String setting(String key, String fallback) {
            String value = System.getProperty(key);
            if (value == null) value = System.getenv(key);
            return value == null ? fallback : value;
        }

        private static void execute(String url, String user, String password, String sql) {
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (Exception error) {
                throw new IllegalStateException("Could not prepare/clean isolated MySQL integration database", error);
            }
        }
    }
}
