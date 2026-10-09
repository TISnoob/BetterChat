package com.tis199.betterchat.common.storage;

import com.tis199.betterchat.common.model.PlayerPreferences;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small pooled JDBC store. Callers never perform SQL on a game or proxy event thread. */
public final class JdbcPreferenceStore implements PreferenceStore {
    private final HikariDataSource dataSource;
    private final ExecutorService databaseExecutor;

    public JdbcPreferenceStore(String jdbcUrl, String username, String password, int poolSize) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username == null ? "" : username);
        config.setPassword(password == null ? "" : password);
        config.setPoolName("BetterChat-Database");
        config.setMaximumPoolSize(Math.max(2, poolSize));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setInitializationFailTimeout(10_000);
        dataSource = new HikariDataSource(config);
        databaseExecutor = Executors.newFixedThreadPool(Math.min(Math.max(2, poolSize), 8), task -> {
            Thread thread = new Thread(task, "BetterChat-Database-Worker");
            thread.setDaemon(true);
            return thread;
        });
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS betterchat_users ("
                    + "uuid VARCHAR(36) PRIMARY KEY, language VARCHAR(128) NOT NULL, "
                    + "country VARCHAR(8) NOT NULL, automatic_country BOOLEAN NOT NULL)");
        }
        if (dataSource.getJdbcUrl().startsWith("jdbc:mariadb:")) {
            try (Connection connection = dataSource.getConnection()) {
                int languageSize = columnSize(connection.getMetaData(), "language");
                int countrySize = columnSize(connection.getMetaData(), "country");
                if ((languageSize > 0 && languageSize < 128) || (countrySize > 0 && countrySize < 8)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("ALTER TABLE betterchat_users "
                                + "MODIFY COLUMN language VARCHAR(128) NOT NULL, "
                                + "MODIFY COLUMN country VARCHAR(8) NOT NULL");
                    }
                }
            }
        }
    }

    private static int columnSize(DatabaseMetaData metadata, String columnName) throws SQLException {
        try (ResultSet columns = metadata.getColumns(null, null, "betterchat_users", columnName)) {
            return columns.next() ? columns.getInt("COLUMN_SIZE") : 0;
        }
    }

    @Override
    public CompletableFuture<Optional<PlayerPreferences>> load(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT language, country, automatic_country FROM betterchat_users WHERE uuid = ?")) {
                statement.setString(1, uniqueId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    return Optional.of(new PlayerPreferences(uniqueId, rows.getString(1), rows.getString(2), rows.getBoolean(3)));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not load BetterChat preferences", exception);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> save(PlayerPreferences preferences) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO betterchat_users(uuid, language, country, automatic_country) VALUES(?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET language=excluded.language, country=excluded.country, "
                    + "automatic_country=excluded.automatic_country";
            // MariaDB uses a different upsert clause; select it from the configured JDBC URL.
            if (dataSource.getJdbcUrl().startsWith("jdbc:mariadb:")) {
                sql = "INSERT INTO betterchat_users(uuid, language, country, automatic_country) VALUES(?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE language=VALUES(language), country=VALUES(country), "
                        + "automatic_country=VALUES(automatic_country)";
            }
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, preferences.uniqueId().toString());
                statement.setString(2, preferences.language());
                statement.setString(3, preferences.country());
                statement.setBoolean(4, preferences.automaticCountry());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not save BetterChat preferences", exception);
            }
        }, databaseExecutor);
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        dataSource.close();
    }
}
