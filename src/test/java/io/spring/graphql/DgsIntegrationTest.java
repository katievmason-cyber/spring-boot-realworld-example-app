package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import io.spring.ApiIntegrationTest;
import io.spring.core.user.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

abstract class DgsIntegrationTest extends ApiIntegrationTest {
  @Autowired protected DgsQueryExecutor executor;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void authenticateAsAnonymous() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "test", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  protected void authenticateAs(User user) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
  }

  protected JsonNode data(String query) {
    return data(executor.execute(query));
  }

  protected JsonNode data(String query, Map<String, Object> variables) {
    return data(executor.execute(query, variables));
  }

  protected JsonNode data(String query, HttpHeaders headers) {
    return data(executor.execute(query, Map.of(), Map.of(), headers));
  }

  private JsonNode data(ExecutionResult result) {
    assertThat(result.getErrors()).isEmpty();
    return objectMapper.valueToTree(result.getData());
  }

  protected void assertGraphQLError(String query, String type, Object... path) {
    ExecutionResult result = executor.execute(query);
    assertThat(result.getErrors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.getPath()).containsExactly(path);
              assertThat(error.getExtensions()).containsEntry("errorType", type);
            });
  }
}
