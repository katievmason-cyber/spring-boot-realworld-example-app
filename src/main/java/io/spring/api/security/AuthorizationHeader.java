package io.spring.api.security;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthorizationHeader {
  private static final Pattern TOKEN_PATTERN = Pattern.compile("^Token (\\S+)$");

  private AuthorizationHeader() {}

  public static Optional<String> extractToken(String header) {
    if (header == null) {
      return Optional.empty();
    }
    Matcher matcher = TOKEN_PATTERN.matcher(header.trim());
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
