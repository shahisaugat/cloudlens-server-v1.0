package com.cloudlenshq.server.auth.controller;

import com.cloudlenshq.server.auth.dto.AuthResponse;
import com.cloudlenshq.server.auth.dto.LoginRequest;
import com.cloudlenshq.server.auth.dto.RegisterRequest;
import com.cloudlenshq.server.auth.dto.UserProfileResponse;
import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
  private final AuthService authService;
  private final com.cloudlenshq.server.auth.repository.UserRepository userRepository;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @RequestHeader("X-Refresh-Token") String refreshToken) {
    AuthResponse response = authService.refresh(refreshToken);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal User currentUser) {
    User freshUser = userRepository.findById(currentUser.getId()).orElse(currentUser);
    UserProfileResponse profile =
        UserProfileResponse.builder()
            .id(freshUser.getId())
            .email(freshUser.getEmail())
            .fullName(freshUser.getFullName())
            .avatarUrl(freshUser.getAvatarUrl())
            .role(freshUser.getRole().name())
            .createdAt(freshUser.getCreatedAt())
            .build();
    return ResponseEntity.ok(profile);
  }

  @GetMapping("/users")
  public ResponseEntity<java.util.List<UserProfileResponse>> getAllUsers() {
    java.util.List<UserProfileResponse> profiles = userRepository.findAll().stream()
        .map(u -> UserProfileResponse.builder()
            .id(u.getId())
            .email(u.getEmail())
            .fullName(u.getFullName())
            .avatarUrl(u.getAvatarUrl())
            .role(u.getRole().name())
            .createdAt(u.getCreatedAt())
            .build())
        .collect(java.util.stream.Collectors.toList());
    return ResponseEntity.ok(profiles);
  }

  @GetMapping("/users/{id}")
  public ResponseEntity<UserProfileResponse> getUserById(@org.springframework.web.bind.annotation.PathVariable Long id) {
    return userRepository.findById(id)
        .map(u -> UserProfileResponse.builder()
            .id(u.getId())
            .email(u.getEmail())
            .fullName(u.getFullName())
            .avatarUrl(u.getAvatarUrl())
            .role(u.getRole().name())
            .createdAt(u.getCreatedAt())
            .build())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
