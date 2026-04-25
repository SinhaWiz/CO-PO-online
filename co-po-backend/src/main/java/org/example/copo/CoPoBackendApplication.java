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
            Admin admin = adminRepository.findByEmail("admin@iut-dhaka.edu").orElse(new Admin());
            admin.setEmail("admin@iut-dhaka.edu");
            admin.setPassword(passwordEncoder.encode("password"));
            admin.setIsSuperAdmin(true);
            admin.setCreatedBy("system");
            adminRepository.save(admin);
            System.out.println("Default admin user created/updated: admin@iut-dhaka.edu / password");
        };
    }
}