package io.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.spring.api.exception.InvalidOperationException;
import io.spring.application.ProfileQueryService;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.data.ProfileData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class MutationGuardsTest {
  private User user;
  private User other;
  private UserRepository userRepository;
  private ProfileQueryService profileQueryService;

  @BeforeEach
  public void setUp() {
    user = new User("me@test.com", "me", "123", "", "");
    other = new User("other@test.com", "other", "123", "", "");
    userRepository = mock(UserRepository.class);
    profileQueryService = mock(ProfileQueryService.class);
    when(userRepository.findByUsername(eq(user.getUsername()))).thenReturn(Optional.of(user));
    when(userRepository.findByUsername(eq(other.getUsername()))).thenReturn(Optional.of(other));
    when(profileQueryService.findByUsername(eq(other.getUsername()), eq(user)))
        .thenReturn(Optional.of(new ProfileData(other.getId(), "other", "", "", true)));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_reject_self_follow() {
    RelationMutation mutation = new RelationMutation(userRepository, profileQueryService);
    assertThrows(InvalidOperationException.class, () -> mutation.follow(user.getUsername()));
    verify(userRepository, never()).saveRelation(any());
  }

  @Test
  public void should_not_duplicate_follow_relation() {
    when(userRepository.findRelation(eq(user.getId()), eq(other.getId())))
        .thenReturn(Optional.of(new FollowRelation(user.getId(), other.getId())));
    new RelationMutation(userRepository, profileQueryService).follow(other.getUsername());
    verify(userRepository, never()).saveRelation(any());
  }

  @Test
  public void should_not_duplicate_favorite() {
    ArticleRepository articleRepository = mock(ArticleRepository.class);
    ArticleFavoriteRepository favoriteRepository = mock(ArticleFavoriteRepository.class);
    Article article = new Article("title", "desc", "body", Arrays.asList("java"), other.getId());
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
    when(favoriteRepository.find(eq(article.getId()), eq(user.getId())))
        .thenReturn(Optional.of(new ArticleFavorite(article.getId(), user.getId())));

    new ArticleMutation(mock(ArticleCommandService.class), favoriteRepository, articleRepository)
        .favoriteArticle(article.getSlug());

    verify(favoriteRepository, never()).save(any());
  }
}
