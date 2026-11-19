package io.spring.api;

import io.spring.api.exception.ResourceNotFoundException;
import io.spring.api.security.AuthorizationHeader;
import io.spring.application.UserQueryService;
import io.spring.application.data.UserData;
import io.spring.application.data.UserWithToken;
import io.spring.application.user.UpdateUserCommand;
import io.spring.application.user.UpdateUserParam;
import io.spring.application.user.UserService;
import io.spring.core.user.User;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/user")
@AllArgsConstructor
public class CurrentUserApi {

  private UserQueryService userQueryService;
  private UserService userService;

  @GetMapping
  public ResponseEntity currentUser(
      @AuthenticationPrincipal User currentUser,
      @RequestHeader(value = "Authorization") String authorization) {
    Optional<String> token = AuthorizationHeader.extractToken(authorization);
    if (token.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    UserData userData =
        userQueryService.findById(currentUser.getId()).orElseThrow(ResourceNotFoundException::new);
    return ResponseEntity.ok(userResponse(new UserWithToken(userData, token.get())));
  }

  @PutMapping
  public ResponseEntity updateProfile(
      @AuthenticationPrincipal User currentUser,
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody UpdateUserParam updateUserParam) {
    Optional<String> token = AuthorizationHeader.extractToken(authorization);
    if (token.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userService.updateUser(new UpdateUserCommand(currentUser, updateUserParam));
    UserData userData =
        userQueryService.findById(currentUser.getId()).orElseThrow(ResourceNotFoundException::new);
    return ResponseEntity.ok(userResponse(new UserWithToken(userData, token.get())));
  }

  private Map<String, Object> userResponse(UserWithToken userWithToken) {
    return new HashMap<String, Object>() {
      {
        put("user", userWithToken);
      }
    };
  }
}
