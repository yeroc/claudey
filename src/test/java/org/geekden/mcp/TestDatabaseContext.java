package org.geekden.mcp;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test-specific context that tracks the current test class name.
 * <p>
 * Uses ThreadLocal storage to support parallel test execution.
 * Each test thread gets its own isolated context, allowing the
 * TestDataSourceProvider to create per-test-class databases.
 * <p>
 * This context is automatically managed by AbstractDatabaseIntegrationTest,
 * which sets the test class name in @BeforeEach and clears it in @AfterEach.
 */
@ApplicationScoped
public class TestDatabaseContext {

  private final ThreadLocal<String> testClassName = new ThreadLocal<>();

  /**
   * Set the current test class name for this thread.
   *
   * @param className Simple name of the test class
   */
  public void setTestClass(String className) {
    testClassName.set(className);
  }

  /**
   * Get the current test class name for this thread.
   *
   * @return Test class name, or null if not set
   */
  public String getTestClass() {
    return testClassName.get();
  }

  /**
   * Clear the test class name for this thread.
   * Should be called in @AfterEach to prevent ThreadLocal leaks.
   */
  public void clear() {
    testClassName.remove();
  }
}
