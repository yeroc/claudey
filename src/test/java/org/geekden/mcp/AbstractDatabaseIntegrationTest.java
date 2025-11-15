package org.geekden.mcp;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Abstract base class for all database integration tests.
 * <p>
 * Automatically sets up test-specific database isolation by registering
 * the test class name in TestDatabaseContext. This allows TestDataSourceProvider
 * to create isolated database files per test class, enabling parallel test execution.
 * <p>
 * All @QuarkusTest classes that use database connections should extend this class.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @QuarkusTest
 * class MyDatabaseTest extends AbstractDatabaseIntegrationTest {
 *   @Inject
 *   DatabaseMcpTools mcpTools;
 *
 *   @Test
 *   void testSomething() {
 *     // Test uses MyDatabaseTest.db automatically
 *   }
 * }
 * }
 * </pre>
 */
public abstract class AbstractDatabaseIntegrationTest {

  @Inject
  TestDatabaseContext testDatabaseContext;

  /**
   * Set up test database context before each test.
   * Registers the test class name so database connections
   * can be isolated per test class.
   *
   * @param testInfo JUnit test information
   */
  @BeforeEach
  void setUpDatabaseContext(TestInfo testInfo) {
    String className = testInfo.getTestClass().get().getSimpleName();
    testDatabaseContext.setTestClass(className);
  }

  /**
   * Clean up test database context after each test.
   * Prevents ThreadLocal memory leaks.
   */
  @AfterEach
  void tearDownDatabaseContext() {
    testDatabaseContext.clear();
  }
}
