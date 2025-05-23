package com.example.userservice.service;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.model.AppUser;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("❌ Email is already in use!");
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());

        // ✅ Always assign default role STUDENT
        Set<String> roles = new HashSet<>();
        roles.add("STUDENT");
        user.setRoles(roles);

        userRepository.save(user);
        return "✅ User registered successfully!";
    }

    public String login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return jwtUtil.generateToken(user);
    }

    public void logout(String token) {
        tokenBlacklistService.revokeToken(token);
    }

    public List<AppUser> getAllActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    public AppUser getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("❌ User not found!"));
    }

    public AppUser updateUser(Long id, AppUser updatedUser) {
        AppUser user = getUserById(id);
        user.setUsername(updatedUser.getUsername());
        user.setEmail(updatedUser.getEmail());
        user.setRoles(updatedUser.getRoles());
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId, String adminEmail) {
        AppUser adminUser = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (!adminUser.getRoles().contains("ADMIN")) {
            throw new RuntimeException("Access Denied: Admin role required");
        }

        AppUser userToDelete = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        userRepository.delete(userToDelete);
    }

    @Transactional
    public void deactivateUser(Long userId, String adminEmail) {
        AppUser adminUser = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (!adminUser.getRoles().contains("ADMIN")) {
            throw new RuntimeException("Access Denied: Admin role required");
        }

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setActive(false);
        userRepository.save(user);
    }

    public String saveProfilePicture(Long userId, MultipartFile file) {
        try {
            Path uploadPath = Paths.get("uploads/profile-pictures/");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            Optional<AppUser> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                user.setProfileImageUrl("/uploads/profile-pictures/" + filename);
                userRepository.save(user);
            }

            return "/uploads/profile-pictures/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store profile picture", e);
        }
    }

    public Long getUserIdByEmail(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return user.getId();
    }

}
