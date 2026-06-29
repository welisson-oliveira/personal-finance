package com.personalfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalfinance.dto.request.RegisterRequest;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  @Test
  void loadUserByUsername_whenUserExists_returnsUserDetails() {
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .name("Test User")
            .email("test@example.com")
            .password("encoded-password")
            .build();
    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

    UserDetails result = userService.loadUserByUsername("test@example.com");

    assertThat(result.getUsername()).isEqualTo("test@example.com");
    assertThat(result.getPassword()).isEqualTo("encoded-password");
  }

  @Test
  void loadUserByUsername_whenUserNotFound_throwsUsernameNotFoundException() {
    when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.loadUserByUsername("missing@example.com"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("missing@example.com");
  }

  @Test
  void register_withNewEmail_savesAndReturnsUser() {
    RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "password123");
    when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
    User saved =
        User.builder()
            .id(UUID.randomUUID())
            .name("John Doe")
            .email("john@example.com")
            .password("hashed-password")
            .build();
    when(userRepository.save(any(User.class))).thenReturn(saved);

    User result = userService.register(request);

    assertThat(result.getEmail()).isEqualTo("john@example.com");
    assertThat(result.getName()).isEqualTo("John Doe");
    verify(passwordEncoder).encode("password123");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void register_withDuplicateEmail_throwsIllegalArgumentException() {
    RegisterRequest request = new RegisterRequest("Jane Doe", "dup@example.com", "password123");
    when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.register(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dup@example.com");

    verify(userRepository, never()).save(any());
  }
}
