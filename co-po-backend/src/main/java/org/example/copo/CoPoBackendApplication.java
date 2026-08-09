package org.example.copo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.example.copo.repository.AdminRepository;
import org.example.copo.entity.Admin;

@SpringBootApplication
public class CoPoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoPoBackendApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataInitializer(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Only seed the default admin the very first time the app runs against an
            // empty Admin table. Re-seeding on every startup used to blow away any
            // password change made through the account management screens.
            if (adminRepository.count() == 0) {
                Admin admin = new Admin();
                admin.setEmail("admin@iut-dhaka.edu");
                admin.setPassword(passwordEncoder.encode("Password123"));
                admin.setIsSuperAdmin(true);
                admin.setCreatedBy("system");
                adminRepository.save(admin);
                System.out.println("Default admin user created: admin@iut-dhaka.edu / Password123");
            }
        };
    }
}
