package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.spring.core.article.Article;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.user.User;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class DateContractTest extends DgsIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CommentRepository commentRepository;

  @Test
  void restAndGraphqlEmitUtcWithExactlyThreeFractionalDigits() throws Exception {
    // Both instants cross the UTC date boundary relative to the source offset.
    DateTime created = DateTime.parse("2024-02-03T00:00:00.000+09:00");
    DateTime updated = created.plusMillis(7);
    String createdUtc = "2024-02-02T15:00:00.000Z";
    String updatedUtc = "2024-02-02T15:00:00.007Z";

    User author = saveUser("author");
    Article article = saveArticle("Timestamp fixture", author, created);
    jdbc.update(
        "update articles set updated_at = ? where id = ?", updated.getMillis(), article.getId());
    Comment comment = new Comment("Timestamp comment", author.getId(), article.getId());
    commentRepository.save(comment);
    jdbc.update(
        "update comments set created_at = ?, updated_at = ? where id = ?",
        updated.getMillis(),
        updated.getMillis(),
        comment.getId());

    JsonNode graphArticle =
        data("{ article(slug: \"timestamp-fixture\") { createdAt updatedAt"
                + " comments(first: 1) { edges { node { id createdAt updatedAt } } } } }")
            .path("article");
    assertThat(graphArticle.path("createdAt").asText()).isEqualTo(createdUtc);
    assertThat(graphArticle.path("updatedAt").asText()).isEqualTo(updatedUtc);
    JsonNode graphComment = graphArticle.at("/comments/edges/0/node");
    assertThat(graphComment.path("id").asText()).isEqualTo(comment.getId());
    assertThat(graphComment.path("createdAt").asText()).isEqualTo(updatedUtc);
    assertThat(graphComment.path("updatedAt").asText()).isEqualTo(updatedUtc);

    mvc.perform(get("/articles/{slug}", article.getSlug()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.article.createdAt").value(createdUtc))
        .andExpect(jsonPath("$.article.updatedAt").value(updatedUtc));
    mvc.perform(get("/articles/{slug}/comments", article.getSlug()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments[0].id").value(comment.getId()))
        .andExpect(jsonPath("$.comments[0].createdAt").value(updatedUtc))
        .andExpect(jsonPath("$.comments[0].updatedAt").value(updatedUtc));
  }
}
