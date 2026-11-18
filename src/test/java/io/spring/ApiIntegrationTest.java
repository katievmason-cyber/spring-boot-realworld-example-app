package io.spring;

import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.List;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:sqlite::memory:",
      "spring.datasource.hikari.maximum-pool-size=1"
    })
@AutoConfigureMockMvc
@Transactional
public abstract class ApiIntegrationTest {
  protected static final String PASSWORD = "test-password";

  @Autowired protected ArticleRepository articleRepository;
  @Autowired protected UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  protected User saveUser(String username) {
    User user =
        new User(username + "@example.com", username, passwordEncoder.encode(PASSWORD), "", "");
    userRepository.save(user);
    return user;
  }

  protected Article saveArticle(String title, User author, DateTime createdAt, String... tags) {
    Article article =
        new Article(title, "Description", "Article body", List.of(tags), author.getId(), createdAt);
    articleRepository.save(article);
    return article;
  }
}
