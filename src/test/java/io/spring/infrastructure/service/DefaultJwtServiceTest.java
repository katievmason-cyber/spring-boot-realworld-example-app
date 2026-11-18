package io.spring.infrastructure.service;

import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultJwtServiceTest {

  private static final String TEST_SECRET =
      "test-only-jwt-secret-do-not-use-in-production-0123456789abcdefghijklmnopqrstuvwxyz";

  private JwtService jwtService;

  @BeforeEach
  public void setUp() {
    jwtService = new DefaultJwtService(TEST_SECRET, 3600);
  }

  @Test
  public void should_reject_missing_secret() {
    Assertions.assertThrows(IllegalStateException.class, () -> new DefaultJwtService(null, 3600));
    Assertions.assertThrows(IllegalStateException.class, () -> new DefaultJwtService("", 3600));
    Assertions.assertThrows(IllegalStateException.class, () -> new DefaultJwtService("  ", 3600));
  }

  @Test
  public void should_reject_secret_shorter_than_64_bytes() {
    String shortSecret = TEST_SECRET.substring(0, DefaultJwtService.MIN_SECRET_BYTES - 1);
    Assertions.assertThrows(
        IllegalStateException.class, () -> new DefaultJwtService(shortSecret, 3600));
  }

  @Test
  public void should_accept_secret_of_exactly_64_bytes() {
    String secret = TEST_SECRET.substring(0, DefaultJwtService.MIN_SECRET_BYTES);
    Assertions.assertDoesNotThrow(() -> new DefaultJwtService(secret, 3600));
  }

  @Test
  public void should_generate_and_parse_token() {
    User user = new User("email@email.com", "username", "123", "", "");
    String token = jwtService.toToken(user);
    Assertions.assertNotNull(token);
    Optional<String> optional = jwtService.getSubFromToken(token);
    Assertions.assertTrue(optional.isPresent());
    Assertions.assertEquals(optional.get(), user.getId());
  }

  @Test
  public void should_get_null_with_wrong_jwt() {
    Optional<String> optional = jwtService.getSubFromToken("123");
    Assertions.assertFalse(optional.isPresent());
  }

  @Test
  public void should_get_null_with_expired_jwt() {
    String token =
        "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhaXNlbnNpeSIsImV4cCI6MTUwMjE2MTIwNH0.SJB-U60WzxLYNomqLo4G3v3LzFxJKuVrIud8D8Lz3-mgpo9pN1i7C8ikU_jQPJGm8HsC1CquGMI-rSuM7j6LDA";
    Assertions.assertFalse(jwtService.getSubFromToken(token).isPresent());
  }
}
