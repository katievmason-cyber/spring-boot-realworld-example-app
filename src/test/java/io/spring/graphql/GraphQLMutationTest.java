package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.article.Article;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class GraphQLMutationTest extends DgsIntegrationTest {
  private static final String CREATE_USER =
      "mutation ($input: CreateUserInput!) { createUser(input: $input) { __typename"
          + " ... on UserPayload { user { username email token profile { username } } }"
          + " ... on Error { message errors { key value } } } }";
  private static final String CREATE_ARTICLE_ARGS =
      "(input: {title: \"New article\", description: \"Description\", body: \"Body\"})";

  @Autowired private JwtService jwtService;
  @Autowired private CommentRepository commentRepository;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void registrationAndLoginAreAvailableWithoutRequiringAnExistingSession(boolean authenticated) {
    if (authenticated) {
      authenticateAs(saveUser("existing"));
    }
    JsonNode registration =
        data(CREATE_USER, registrationInput("new-user@example.com")).path("createUser");
    assertThat(registration.path("__typename").asText()).isEqualTo("UserPayload");
    assertUserPayload(registration.path("user"), "new-user", "new-user@example.com");

    JsonNode login =
        data(
            "mutation ($password: String!) { login(email: \"new-user@example.com\", password: $password)"
                + " { user { username email token profile { username } } } }",
            Map.of("password", PASSWORD));
    assertUserPayload(login.at("/login/user"), "new-user", "new-user@example.com");
  }

  @Test
  void invalidRegistrationUsesTheErrorUnion() {
    JsonNode result = data(CREATE_USER, registrationInput("invalid-email")).path("createUser");
    assertThat(result.path("__typename").asText()).isEqualTo("Error");
    assertThat(result.path("message").asText()).isEqualTo("BAD_REQUEST");
    assertThat(result.path("errors")).hasSize(1);
    assertThat(result.at("/errors/0/key").asText()).isEqualTo("email");
    assertThat(result.at("/errors/0/value/0").asText()).isEqualTo("should be an email");
  }

  @Test
  void badLoginMapsToUnauthenticated() {
    saveUser("reader");
    String query =
        "mutation { login(email: \"reader@example.com\", password: \"wrong\") { user { username } } }";
    assertThat(executor.execute(query).getErrors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.getPath()).containsExactly("login");
              assertThat(error.getMessage()).isEqualTo("invalid email or password");
              assertThat(error.getExtensions()).containsEntry("errorType", "UNAUTHENTICATED");
            });
  }

  @Test
  void updateUserReturnsNullForAnonymousAndPersistsChangesForTheSignedInUser() {
    String query =
        "mutation { updateUser(changes: {bio: \"Updated bio\"})"
            + " { user { username profile { bio } } } }";
    assertThat(data(query).path("updateUser").isNull()).isTrue();

    User reader = saveUser("reader");
    authenticateAs(reader);
    JsonNode updated = data(query).at("/updateUser/user");
    assertThat(updated.path("username").asText()).isEqualTo(reader.getUsername());
    assertThat(updated.at("/profile/bio").asText()).isEqualTo("Updated bio");
    assertThat(userRepository.findById(reader.getId()))
        .get()
        .extracting(User::getBio)
        .isEqualTo("Updated bio");
  }

  @Test
  void updatingToAnExistingUsernameMapsToBadRequestWithFieldDetails() {
    saveUser("taken");
    authenticateAs(saveUser("reader"));
    String query = "mutation { updateUser(changes: {username: \"taken\"}) { user { username } } }";
    assertThat(executor.execute(query).getErrors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.getPath()).containsExactly("updateUser");
              assertThat(error.getExtensions())
                  .containsEntry("errorType", "BAD_REQUEST")
                  .containsEntry("username", List.of("username already exist"));
            });
  }

  @Test
  void followsAndUnfollowsAnotherUser() {
    User reader = saveUser("reader");
    User author = saveUser("author");
    authenticateAs(reader);
    String argumentsAndSelection = "(username: \"author\") { profile { username following } }";
    JsonNode followed =
        data("mutation { followUser" + argumentsAndSelection + " }").at("/followUser/profile");
    assertThat(followed.path("username").asText()).isEqualTo(author.getUsername());
    assertThat(followed.path("following").booleanValue()).isTrue();
    assertThat(userRepository.findRelation(reader.getId(), author.getId())).isPresent();

    JsonNode unfollowed =
        data("mutation { unfollowUser" + argumentsAndSelection + " }").at("/unfollowUser/profile");
    assertThat(unfollowed.path("username").asText()).isEqualTo(author.getUsername());
    assertThat(unfollowed.path("following").booleanValue()).isFalse();
    assertThat(userRepository.findRelation(reader.getId(), author.getId())).isEmpty();
  }

  @Test
  void createsUpdatesAndDeletesAnArticleAsItsAuthor() {
    User author = saveUser("author");
    authenticateAs(author);
    JsonNode created =
        data("mutation { createArticle"
                + CREATE_ARTICLE_ARGS
                + " { article { slug body author { username } } } }")
            .at("/createArticle/article");
    assertThat(created.path("slug").asText()).isEqualTo("new-article");
    assertThat(created.path("body").asText()).isEqualTo("Body");
    assertThat(created.at("/author/username").asText()).isEqualTo(author.getUsername());

    JsonNode updated =
        data("mutation { updateArticle(slug: \"new-article\", changes: {title: \"Updated article\"})"
                + " { article { slug title } } }")
            .at("/updateArticle/article");
    assertThat(updated.path("slug").asText()).isEqualTo("updated-article");
    assertThat(updated.path("title").asText()).isEqualTo("Updated article");

    assertThat(
            data("mutation { deleteArticle(slug: \"updated-article\") { success } }")
                .at("/deleteArticle/success")
                .booleanValue())
        .isTrue();
    assertThat(articleRepository.findBySlug("updated-article")).isEmpty();
  }

  @Test
  void favoritesAndUnfavoritesAnArticleAsTheSignedInReader() {
    saveArticle("Existing article", saveUser("author"), new DateTime());
    authenticateAs(saveUser("reader"));
    String argsAndSelection =
        "(slug: \"existing-article\") { article { slug favorited favoritesCount } }";
    JsonNode favorite =
        data("mutation { favoriteArticle" + argsAndSelection + " }").at("/favoriteArticle/article");
    assertThat(favorite.path("slug").asText()).isEqualTo("existing-article");
    assertThat(favorite.path("favorited").booleanValue()).isTrue();
    assertThat(favorite.path("favoritesCount").asInt()).isEqualTo(1);

    JsonNode unfavorite =
        data("mutation { unfavoriteArticle" + argsAndSelection + " }")
            .at("/unfavoriteArticle/article");
    assertThat(unfavorite.path("slug").asText()).isEqualTo("existing-article");
    assertThat(unfavorite.path("favorited").booleanValue()).isFalse();
    assertThat(unfavorite.path("favoritesCount").asInt()).isZero();
  }

  @Test
  void addsAndDeletesACommentAsItsAuthor() {
    Article article = saveArticle("Existing article", saveUser("author"), new DateTime());
    User reader = saveUser("reader");
    authenticateAs(reader);
    JsonNode comment =
        data("mutation { addComment(slug: \"existing-article\", body: \"Reader comment\")"
                + " { comment { id body author { username } } } }")
            .at("/addComment/comment");
    String id = comment.path("id").asText();
    assertThat(id).isNotBlank();
    assertThat(comment.path("body").asText()).isEqualTo("Reader comment");
    assertThat(comment.at("/author/username").asText()).isEqualTo(reader.getUsername());
    assertThat(commentRepository.findById(article.getId(), id)).isPresent();

    assertThat(
            data(
                    "mutation ($id: ID!) { deleteComment(slug: \"existing-article\", id: $id) { success } }",
                    Map.of("id", id))
                .at("/deleteComment/success")
                .booleanValue())
        .isTrue();
    assertThat(commentRepository.findById(article.getId(), id)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("protectedMutations")
  void protectedWritesUseTheCurrentDgsFallbackForAnonymousRequests(String field, String selection) {
    saveArticle("Existing article", saveUser("author"), new DateTime());
    assertGraphQLError("mutation { " + field + selection + " }", "INTERNAL", field);
  }

  static Stream<Arguments> protectedMutations() {
    return Stream.of(
        arguments("followUser", "(username: \"author\") { profile { username } }"),
        arguments("unfollowUser", "(username: \"author\") { profile { username } }"),
        arguments("createArticle", CREATE_ARTICLE_ARGS + " { article { slug } }"),
        arguments(
            "updateArticle",
            "(slug: \"existing-article\", changes: {title: \"Changed\"}) { article { slug } }"),
        arguments("favoriteArticle", "(slug: \"existing-article\") { article { slug } }"),
        arguments("unfavoriteArticle", "(slug: \"existing-article\") { article { slug } }"),
        arguments("deleteArticle", "(slug: \"existing-article\") { success }"),
        arguments(
            "addComment", "(slug: \"existing-article\", body: \"Comment\") { comment { id } }"),
        arguments("deleteComment", "(slug: \"existing-article\", id: \"unused\") { success }"));
  }

  @Test
  void unrelatedReaderCannotWriteSomeoneElsesArticleOrComment() {
    User author = saveUser("author");
    Article article = saveArticle("Existing article", author, new DateTime());
    Comment comment = new Comment("Author comment", author.getId(), article.getId());
    commentRepository.save(comment);
    authenticateAs(saveUser("reader"));
    assertGraphQLError(
        "mutation { updateArticle(slug: \"existing-article\", changes: {title: \"Changed\"})"
            + " { article { slug } } }",
        "INTERNAL",
        "updateArticle");
    assertGraphQLError(
        "mutation { deleteArticle(slug: \"existing-article\") { success } }",
        "INTERNAL",
        "deleteArticle");
    assertGraphQLError(
        String.format(
            "mutation { deleteComment(slug: \"existing-article\", id: \"%s\") { success } }",
            comment.getId()),
        "INTERNAL",
        "deleteComment");
    assertThat(articleRepository.findBySlug(article.getSlug())).isPresent();
    assertThat(commentRepository.findById(article.getId(), comment.getId())).isPresent();
  }

  private static Map<String, Object> registrationInput(String email) {
    return Map.of("input", Map.of("username", "new-user", "email", email, "password", PASSWORD));
  }

  private void assertUserPayload(JsonNode user, String username, String email) {
    assertThat(user.path("username").asText()).isEqualTo(username);
    assertThat(user.path("email").asText()).isEqualTo(email);
    assertThat(user.at("/profile/username").asText()).isEqualTo(username);
    assertThat(jwtService.getSubFromToken(user.path("token").asText()))
        .contains(userRepository.findByUsername(username).orElseThrow().getId());
  }
}
