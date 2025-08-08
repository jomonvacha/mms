package com.roots.mms.config;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
    }

    private void initializeRoles() {
        for (ERole eRole : ERole.values()) {
            if (!roleRepository.findByName(eRole).isPresent()) {
                Role role = new Role(eRole);
                roleRepository.save(role);
                System.out.println("Created role: " + eRole.name());
            }
        }
    }
}
