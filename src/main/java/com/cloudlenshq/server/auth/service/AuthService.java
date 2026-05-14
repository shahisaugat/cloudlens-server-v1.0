package com.cloudlenshq.server.auth.service;

import com.cloudlenshq.server.auth.dto.AuthResponse;
import com.cloudlenshq.server.auth.dto.LoginRequest;
import com.cloudlenshq.server.auth.dto.RegisterRequest;
import com.cloudlenshq.server.auth.entity.Role;
import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.auth.exception.EmailAlreadyExistsException;
import com.cloudlenshq.server.auth.exception.InvalidTokenException;
import com.cloudlenshq.server.auth.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
    }

    User user =
        User.builder()
            .fullName(request.getFullName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(Role.USER)
            .build();

    userRepository.save(user);
    log.info("New user registered: {}", user.getEmail());

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    return buildAuthResponse(user, accessToken, refreshToken);
  }

  public AuthResponse login(LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(
                () -> new UsernameNotFoundException("User not found: " + request.getEmail()));

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    log.info("User logged in: {}", user.getEmail());

    return buildAuthResponse(user, accessToken, refreshToken);
  }

  public AuthResponse refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new InvalidTokenException("Refresh token is required");
    }

    final String userEmail;

    try {
      userEmail = jwtService.extractUsername(refreshToken);
    } catch (JwtException e) {
      throw new InvalidTokenException("Invalid refresh token");
    }

    User user =
        userRepository
            .findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));

    if (!jwtService.isTokenValid(refreshToken, user) || !jwtService.isRefreshToken(refreshToken)) {
      throw new InvalidTokenException("Refresh token is invalid or expired");
    }

    String newAccessToken = jwtService.generateAccessToken(user);
    String newRefreshToken = jwtService.generateRefreshToken(user);

    log.info("Tokens refreshed for user: {}", user.getEmail());

    return buildAuthResponse(user, newAccessToken, newRefreshToken);
  }

  private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresIn(900000L)
        .email(user.getEmail())
        .fullName(user.getFullName())
        .avatarUrl(user.getAvatarUrl())
        .role(user.getRole().name())
        .build();
  }
}
