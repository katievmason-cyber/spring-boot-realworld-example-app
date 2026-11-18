package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.article.Article;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import java.util.Map;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class GraphQLQueryTest extends DgsIntegrationTest {
  @Autowired private CommentRepository commentRepository;
  @Autowired private ArticleFavoriteRepository favoriteRepository;

  private User author;
  private User reader;
  private Article article;

  @BeforeEach
  void seedArticlesAndReader() {
    author = saveUser("author");
    reader = saveUser("reader");
    article = saveArticle("Featured article", author, new DateTime(), "spring");
    saveArticle("Other article", author, article.getCreatedAt().minusMillis(1), "other");
    userRepository.saveRelation(new FollowRelation(reader.getId(), author.getId()));
    favoriteRepository.save(new ArticleFavorite(article.getId(), reader.getId()));
    commentRepository.save(new Comment("Reader comment", reader.getId(), article.getId()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publicQueriesResolveNestedDataWithViewerSpecificFlags(boolean authenticated) {
    if (authenticated) {
      authenticateAs(reader);
    }
    JsonNode response =
        data(
            "query ($slug: String!) {"
                + " article(slug: $slug) { slug favorited author { username following }"
                + "   comments(first: 5) { edges { node { body author { username } } } } }"
                + " articles(first: 5, authoredBy: \"author\", withTag: \"spring\")"
                + "   { edges { node { slug } } }"
                + " profile(username: \"author\") { profile { username following"
                + "   articles(first: 5) { edges { node { slug } } } } }"
                + " reader: profile(username: \"reader\") { profile {"
                + "   favorites(first: 5) { edges { node { slug favorited favoritesCount } } }"
                + "   feed(first: 5) { edges { node { slug } } } } }"
                + " tags }",
            Map.of("slug", article.getSlug()));

    assertThat(response.at("/article/slug").asText()).isEqualTo(article.getSlug());
    assertThat(response.at("/article/favorited").booleanValue()).isEqualTo(authenticated);
    assertThat(response.at("/article/author/username").asText()).isEqualTo(author.getUsername());
    assertThat(response.at("/article/author/following").booleanValue()).isEqualTo(authenticated);
    assertThat(response.at("/article/comments/edges")).hasSize(1);
    assertThat(response.at("/article/comments/edges/0/node/body").asText())
        .isEqualTo("Reader comment");
    assertThat(response.at("/article/comments/edges/0/node/author/username").asText())
        .isEqualTo(reader.getUsername());

    assertThat(response.at("/articles/edges")).hasSize(1);
    assertThat(response.at("/articles/edges/0/node/slug").asText()).isEqualTo(article.getSlug());
    assertThat(response.at("/profile/profile/username").asText()).isEqualTo(author.getUsername());
    assertThat(response.at("/profile/profile/following").booleanValue()).isEqualTo(authenticated);
    assertThat(response.at("/profile/profile/articles/edges")).hasSize(2);
    assertThat(response.at("/profile/profile/articles/edges/0/node/slug").asText())
        .isEqualTo(article.getSlug());
    assertThat(response.at("/profile/profile/articles/edges/1/node/slug").asText())
        .isEqualTo("other-article");

    assertThat(response.at("/reader/profile/favorites/edges")).hasSize(1);
    JsonNode favorite = response.at("/reader/profile/favorites/edges/0/node");
    assertThat(favorite.path("slug").asText()).isEqualTo(article.getSlug());
    assertThat(favorite.path("favorited").booleanValue()).isEqualTo(authenticated);
    assertThat(favorite.path("favoritesCount").asInt()).isEqualTo(1);
    assertThat(response.at("/reader/profile/feed/edges")).hasSize(2);
    assertThat(response.at("/reader/profile/feed/edges/0/node/slug").asText())
        .isEqualTo(article.getSlug());
    assertThat(response.at("/tags"))
        .extracting(JsonNode::asText)
        .containsExactlyInAnyOrder("spring", "other");
  }

  @Test
  void meIsNullForAnonymousAndReturnsTheSignedInUserWithTheirToken() {
    String query = "{ me { username email token profile { username } } }";
    assertGraphQLError(query, "INTERNAL", "me");

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Token example-token");
    assertThat(data(query, headers).path("me").isNull()).isTrue();

    authenticateAs(reader);
    JsonNode me = data(query, headers).path("me");
    assertThat(me.path("username").asText()).isEqualTo(reader.getUsername());
    assertThat(me.path("email").asText()).isEqualTo(reader.getEmail());
    assertThat(me.path("token").asText()).isEqualTo("example-token");
    assertThat(me.at("/profile/username").asText()).isEqualTo(reader.getUsername());
  }

  @Test
  void feedRequiresAViewerAndReturnsFollowedAuthorsArticles() {
    String query = "{ feed(first: 5) { edges { node { slug } } } }";
    assertGraphQLError(query, "INTERNAL", "feed");

    authenticateAs(reader);
    assertThat(data(query).at("/feed/edges")).hasSize(2);
    assertThat(data(query).at("/feed/edges/0/node/slug").asText()).isEqualTo(article.getSlug());
  }

  @ParameterizedTest
  @ValueSource(strings = {"article", "profile", "articles"})
  void missingResourcesAndMissingPageSizeUseTheDgsFallback(String field) {
    Map<String, String> selections =
        Map.of(
            "article", "article(slug: \"missing\") { slug }",
            "profile", "profile(username: \"missing\") { profile { username } }",
            "articles", "articles { edges { cursor } }");
    assertGraphQLError("{ " + selections.get(field) + " }", "INTERNAL", field);
  }

  @Test
  void commentArticleCurrentlyMapsItsIncompatibleLocalContextToAnInternalError() {
    assertGraphQLError(
        "{ article(slug: \"featured-article\") { comments(first: 1)"
            + " { edges { node { article { slug } } } } } }",
        "INTERNAL",
        "article",
        "comments",
        "edges",
        0,
        "node",
        "article");
  }
}
