package com.roots.mms.service;

import com.roots.mms.dto.request.ChangePasswordRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.dto.request.UpdateUserRolesRequest;
import com.roots.mms.dto.request.UpdateUserStatusRequest;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  public UserResponse getUserById(Long id) {
    User user = userRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    return toResponse(user);
  }

    public UserResponse updateProfile(Long id, UpdateUserProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (request.getEmail() != null) {
            String newEmail = request.getEmail();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    throw new DuplicateResourceException("User", "email", newEmail);
                }
                user.setEmail(newEmail);
            }
        }
        if (request.getFirstName() != null) {
            String fn = request.getFirstName().trim();
            if (fn.isEmpty()) throw new BusinessRuleException("First name cannot be empty");
            user.setFirstName(fn);
        }
        if (request.getLastName() != null) {
            String ln = request.getLastName().trim();
            if (ln.isEmpty()) throw new BusinessRuleException("Last name cannot be empty");
            user.setLastName(ln);
        }
        if (request.getPhoneNumber() != null) {
            String phone = request.getPhoneNumber().trim();
            if (!phone.isEmpty()) {
                // Basic phone validation (E.164-ish / common formats)
                if (!phone.matches("^\\+?[0-9.\\-\\s()]{7,20}$")) {
                    throw new BusinessRuleException("Invalid phone number format");
                }
            }
            user.setPhoneNumber(phone.isEmpty() ? null : phone);
        }
        userRepository.save(user);
        return toResponse(user);
    }

    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        String newPw = request.getNewPassword();
        if (newPw == null || newPw.length() < 8 || !newPw.matches(".*[A-Za-z].*") || !newPw.matches(".*[0-9].*")) {
            throw new BusinessRuleException("New password must be at least 8 characters and include letters and numbers");
        }
        if (passwordEncoder.matches(newPw, user.getPassword())) {
            throw new BusinessRuleException("New password must be different from current password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

  public Page<UserResponse> listUsers(int page, int size, String sortBy, String sortDir) {
    Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(page, size, sort);
    return userRepository.findAll(pageable).map(this::toResponse);
  }

  public UserResponse updateRoles(Long id, UpdateUserRolesRequest request) {
    User user = userRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    Set<Role> newRoles = new HashSet<>();
    for (String roleName : request.getRoles()) {
      ERole eRole;
      try {
        eRole = ERole.valueOf(roleName);
      } catch (IllegalArgumentException ex) {
        throw new BusinessRuleException("Invalid role: " + roleName);
      }
      Role role = roleRepository.findByName(eRole)
        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", eRole));
      newRoles.add(role);
    }
    user.setRoles(newRoles);
    userRepository.save(user);
    return toResponse(user);
  }

  public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
    User user = userRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    user.setActive(request.getActive());
    userRepository.save(user);
    return toResponse(user);
  }

  private UserResponse toResponse(User user) {
    UserResponse response = new UserResponse();
    response.setId(user.getId());
    response.setUsername(user.getUsername());
    response.setEmail(user.getEmail());
    response.setFirstName(user.getFirstName());
    response.setLastName(user.getLastName());
    response.setPhoneNumber(user.getPhoneNumber());
    response.setActive(user.getActive());
    response.setCreatedAt(user.getCreatedAt());
    response.setUpdatedAt(user.getUpdatedAt());
    response.setRoles(user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toList()));
    // If password is empty or null, treat as no local password (OAuth-only)
    response.setHasPassword(user.getPassword() != null && !user.getPassword().isBlank());
    return response;
  }
}
