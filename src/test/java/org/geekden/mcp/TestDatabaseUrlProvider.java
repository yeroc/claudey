package org.geekden.mcp;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.geekden.mcp.service.DatabaseUrlProvider;
import org.jboss.logging.Logger;

import java.sql.SQLException;

/**
 * Test-specific implementation of DatabaseUrlProvider.
 * <p>
 * Transforms JDBC URLs to provide test-class-specific isolation.
 * For SQLite file-based databases, automatically derives unique filenames
 * from the test class name (e.g., "CliQueryTest.db"). This enables
 * parallel test execution with proper database isolation.
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestDatabaseUrlProvider implements DatabaseUrlProvider {

  private static final Logger LOG = Logger.getLogger(TestDatabaseUrlProvider.class);

  @Inject
  TestDatabaseContext testDatabaseContext;

  @Override
  public String transformUrl(String originalUrl) {
    // For SQLite file-based databases, isolate per test class
    if (originalUrl.matches("jdbc:sqlite:.*\\.db")) {
      String testClassName = testDatabaseContext.getTestClass();
      if (testClassName == null) {
        throw new RuntimeException(
            "Test class name not set in TestDatabaseContext. " +
            "Database tests must extend AbstractDatabaseIntegrationTest."
        );
      }
      // Replace filename with test-class-specific name (e.g., CliQueryTest.db)
      String transformedUrl = originalUrl.replaceFirst("[^/]+\\.db$", testClassName + ".db");
      LOG.debug("Using isolated test database for " + testClassName + ": " + transformedUrl);
      return transformedUrl;
    }

    return originalUrl;
  }
}
