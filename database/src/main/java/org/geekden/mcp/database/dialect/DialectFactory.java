package org.geekden.mcp.database.dialect;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

@ApplicationScoped
public class DialectFactory {
  private static final Logger LOG = Logger.getLogger(DialectFactory.class);

  private static final List<DatabaseDialect> DIALECTS = List.of(
      new SQLiteDialect(),
      new StandardDialect() // Catch-all must be last
  );

  public DatabaseDialect getDialect(DatabaseMetaData metaData) throws SQLException {
    String dbName = metaData.getDatabaseProductName().toLowerCase();
    LOG.debugf("Detecting dialect for database: %s", dbName);

    for (DatabaseDialect dialect : DIALECTS) {
      if (dialect.accepts(metaData)) {
        LOG.infof("Using %s dialect", dialect.getName());
        return dialect;
      }
    }

    throw new SQLException("No suitable dialect found for database: " + dbName);
  }
}
