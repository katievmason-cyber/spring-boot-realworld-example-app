package io.spring.api;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.spring.ApiIntegrationTest;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import java.util.stream.Stream;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class WebSecurityMatrixTest extends ApiIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;

  private String token;

  @BeforeEach
  void prepareAuthenticatedViewerAndPublicArticle() {
    User author = saveUser("author");
    saveArticle("Public article", author, new DateTime());
    token = jwtService.toToken(author);
  }

  @ParameterizedTest(name = "{0} {1} → anonymous {3}, authenticated {4}")
  @MethodSource("rules")
  void reportsExpectedStatusWithAndWithoutRealJwt(
      HttpMethod method, String path, String json, int anonymous, int authenticated)
      throws Exception {
    mvc.perform(buildRequest(method, path, json)).andExpect(status().is(anonymous));
    mvc.perform(
            buildRequest(method, path, json).header(HttpHeaders.AUTHORIZATION, "Token " + token))
        .andExpect(status().is(authenticated));
  }

  static Stream<Arguments> rules() {
    // Invalid write bodies confirm that permitted requests reach MVC without changing fixtures.
    String invalidUser = "{\"user\":{}}";
    String invalidArticle = "{\"article\":{\"title\":\"Draft\"}}";
    return Stream.of(
        arguments(OPTIONS, "/user", null, 200, 200),
        arguments(GET, "/graphiql", null, 200, 200),
        arguments(POST, "/graphql", "{\"query\":\"{ tags }\"}", 200, 200),
        arguments(GET, "/articles/feed", null, 401, 200),
        arguments(POST, "/users", invalidUser, 422, 422),
        arguments(POST, "/users/login", invalidUser, 422, 422),
        arguments(GET, "/articles", null, 200, 200),
        arguments(GET, "/articles/", null, 200, 200),
        arguments(GET, "/articles/public-article", null, 200, 200),
        arguments(GET, "/articles/public-article/", null, 200, 200),
        arguments(GET, "/articles/public-article/comments", null, 200, 200),
        arguments(GET, "/profiles/author", null, 200, 200),
        arguments(GET, "/profiles/author/", null, 200, 200),
        arguments(GET, "/tags", null, 200, 200),
        arguments(GET, "/tags/", null, 401, 200),
        arguments(HEAD, "/tags", null, 401, 200),
        arguments(GET, "/user", null, 401, 200),
        arguments(GET, "/user/", null, 401, 200),
        arguments(POST, "/articles", invalidArticle, 401, 422),
        arguments(POST, "/articles/", invalidArticle, 401, 422),
        arguments(POST, "/users/", invalidUser, 401, 422),
        arguments(POST, "/users/login/", invalidUser, 401, 422));
  }

  @Test
  void anonymousCorsPreflightAllowsAuthorizationHeaderOnProtectedRoute() throws Exception {
    mvc.perform(
            request(OPTIONS, "/user")
                .header(HttpHeaders.ORIGIN, "https://example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization"));
  }

  private static MockHttpServletRequestBuilder buildRequest(
      HttpMethod method, String path, String json) {
    MockHttpServletRequestBuilder request = request(method, path);
    if (json != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    return request;
  }
}
