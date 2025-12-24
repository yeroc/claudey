package org.geekden.mcp.database.dialect;

/**
 * Factory methods for creating database paginators.
 */
class Paginators {

  /**
   * Standard pagination: LIMIT/OFFSET syntax.
   * Works for PostgreSQL, MySQL, SQLite, and most others.
   */
  static DatabaseDialect.Paginator standard() {
    return (query, offset, limit) ->
        query.trim() + " LIMIT " + limit + " OFFSET " + offset;
  }
}
