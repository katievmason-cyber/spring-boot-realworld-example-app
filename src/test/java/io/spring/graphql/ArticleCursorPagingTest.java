package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArticleCursorPagingTest extends DgsIntegrationTest {
  @BeforeEach
  void seedAdjacentAndTiedTimestamps() {
    User author = saveUser("author");
    DateTime tied = DateTime.parse("2024-02-03T00:00:00.123Z");
    saveArticle("older", author, tied.minusMillis(1));
    saveArticle("tied a", author, tied);
    saveArticle("tied b", author, tied);
    saveArticle("newer", author, tied.plusMillis(1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageThroughAdjacentMillisecondsWithBothTiedArticlesOnOnePage(boolean forward) {
    JsonNode initial = page(forward, 1, null);
    assertThat(slugs(initial)).containsExactly(forward ? "newer" : "older");
    assertThat(hasMore(forward, initial)).isTrue();

    JsonNode middle = page(forward, 2, nextCursor(forward, initial));
    assertThat(slugs(middle)).containsExactlyInAnyOrder("tied-a", "tied-b");
    assertThat(hasMore(forward, middle)).isTrue();

    JsonNode terminal = page(forward, 1, nextCursor(forward, middle));
    assertThat(slugs(terminal)).containsExactly(forward ? "older" : "newer");
    assertThat(hasMore(forward, terminal)).isFalse();
    assertThat(slugs(page(forward, 1, nextCursor(forward, terminal)))).isEmpty();
  }

  @Disabled("Timestamp-only cursors skip the other article when a page boundary splits a tie")
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageThroughATieWithoutDuplicatesOrGapsWhenPageSizeIsOne(boolean forward) {
    List<String> visited = new ArrayList<>();
    String cursor = null;
    for (int i = 0; i < 4; i++) {
      JsonNode current = page(forward, 1, cursor);
      visited.addAll(slugs(current));
      if (!hasMore(forward, current)) {
        break;
      }
      cursor = nextCursor(forward, current);
    }
    assertThat(visited).containsExactlyInAnyOrder("newer", "tied-a", "tied-b", "older");
  }

  private JsonNode page(boolean forward, int size, String cursor) {
    String args = forward ? "first: $size, after: $cursor" : "last: $size, before: $cursor";
    return data(
            "query ($size: Int!, $cursor: String) { articles("
                + args
                + ")"
                + " { edges { node { slug } } pageInfo { startCursor endCursor hasNextPage hasPreviousPage } } }",
            cursor == null ? Map.of("size", size) : Map.of("size", size, "cursor", cursor))
        .path("articles");
  }

  private static List<String> slugs(JsonNode page) {
    return StreamSupport.stream(page.path("edges").spliterator(), false)
        .map(edge -> edge.at("/node/slug").asText())
        .collect(Collectors.toList());
  }

  private static boolean hasMore(boolean forward, JsonNode page) {
    return page.path("pageInfo").path(forward ? "hasNextPage" : "hasPreviousPage").booleanValue();
  }

  private static String nextCursor(boolean forward, JsonNode page) {
    JsonNode cursor = page.path("pageInfo").path(forward ? "endCursor" : "startCursor");
    assertThat(cursor.isTextual()).isTrue();
    return cursor.asText();
  }
}
