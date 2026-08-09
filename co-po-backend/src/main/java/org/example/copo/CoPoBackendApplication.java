package org.example.copo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.InfoResult;
import org.example.copo.security.PasswordMatcher;
import org.example.copo.repository.AdminRepository;
import org.example.copo.entity.Admin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

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
                InfoResult info = flyway.info();
                long pending = info.pending().length;
                long success = info.applied().length;
                System.out.println(
                        "Startup diagnostics: Flyway applied migrations = " + success + ", pending migrations = " + pending);
            } catch (Exception ex) {
                System.out.println("Startup diagnostics: Flyway info lookup failed: " + ex.getMessage());
            }
        };
    }

    @Bean
    public CommandLineRunner dataInitializer(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            PasswordMatcher passwordMatcher,
            JdbcTemplate jdbcTemplate) {
        return args -> {
            Integer adminTableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'Admin'",
                    Integer.class);

            if (adminTableCount == null || adminTableCount == 0) {
                System.out.println("Admin table is missing in the current database; skipping default admin seeding.");
                return;
            }

            // Only seed the default admin the very first time the app runs against an
            // empty Admin table. Re-seeding on every startup used to blow away any
            // password change made through the account management screens.
            adminRepository.findByEmail("admin@iut-dhaka.edu").ifPresentOrElse(admin -> {
                // Repair the known legacy default accounts if we find them in the wild.
                // This keeps real password changes intact while still unblocking fresh
                // deployments that were seeded with the wrong default password.
                if (passwordMatcher.matches("password", admin.getPassword())) {
                    admin.setPassword(passwordEncoder.encode("Password123"));
                    admin.setIsSuperAdmin(true);
                    admin.setCreatedBy("system");
                    adminRepository.save(admin);
                    System.out.println("Default admin password repaired: admin@iut-dhaka.edu / Password123");
                }
            }, () -> {
                Admin admin = new Admin();
                admin.setEmail("admin@iut-dhaka.edu");
                admin.setPassword(passwordEncoder.encode("Password123"));
                admin.setIsSuperAdmin(true);
                admin.setCreatedBy("system");
                adminRepository.save(admin);
                System.out.println("Default admin user created: admin@iut-dhaka.edu / Password123");
            });
        };
    }
}
