package com.roots.mms.repository;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

  Optional<Role> findByName(ERole name);
}
