package org.example.copo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.example.copo.security.PasswordMatcher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@SpringBootApplication
public class CoPoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoPoBackendApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupDiagnostics(
            Environment environment,
            DataSource dataSource,
            Flyway flyway) {
        return args -> {
            String activeProfiles = String.join(",", environment.getActiveProfiles());
            if (activeProfiles.isBlank()) {
                activeProfiles = "(none)";
            }

            String jdbcUrl = "(unavailable)";
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                jdbcUrl = metaData.getURL();
            } catch (Exception ex) {
                System.out.println("Startup diagnostics: unable to read datasource URL: " + ex.getMessage());
            }

            System.out.println("Startup diagnostics: active profiles = " + activeProfiles);
            System.out.println("Startup diagnostics: datasource URL = " + jdbcUrl);

            try {
                MigrationInfoService info = flyway.info();
                MigrationInfo[] pending = info.pending();
                MigrationInfo[] applied = info.applied();
                System.out.println(
                        "Startup diagnostics: Flyway applied migrations = " + applied.length + ", pending migrations = " + pending.length);
            } catch (Exception ex) {
                System.out.println("Startup diagnostics: Flyway info lookup failed: " + ex.getMessage());
            }
        };
    }

    @Bean
    public CommandLineRunner dataInitializer(
            PasswordMatcher passwordMatcher,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        return args -> {
            Integer adminTableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'Admin'",
                    Integer.class);

            if (adminTableCount == null || adminTableCount == 0) {
                System.out.println("Admin table is missing in the current database; skipping default admin seeding.");
                return;
            }

            Integer adminCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Admin WHERE email = ?",
                    Integer.class,
                    "admin@iut-dhaka.edu");

            if (adminCount != null && adminCount > 0) {
                String storedPassword = jdbcTemplate.queryForObject(
                        "SELECT password FROM Admin WHERE email = ? LIMIT 1",
                        String.class,
                        "admin@iut-dhaka.edu");

                if (!passwordMatcher.matches("Password123", storedPassword)) {
                    jdbcTemplate.update(
                        "UPDATE Admin SET password = ?, is_super_admin = TRUE, created_by = 'system' WHERE email = ?",
                        passwordEncoder.encode("Password123"),
                        "admin@iut-dhaka.edu");
                    System.out.println("Default admin password reset: admin@iut-dhaka.edu / Password123");
                }
                return;
            }

            jdbcTemplate.update(
                    "INSERT INTO Admin (email, password, is_super_admin, created_by) VALUES (?, ?, TRUE, 'system')",
                    "admin@iut-dhaka.edu",
                    passwordEncoder.encode("Password123"));
            System.out.println("Default admin user created: admin@iut-dhaka.edu / Password123");
        };
    }
}
