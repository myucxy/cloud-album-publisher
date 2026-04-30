package com.cloudalbum.publisher.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class FlywayMigrationConfig {

    private static final String[] REQUIRED_TABLES = {
            "t_user",
            "t_role",
            "t_user_role",
            "t_album",
            "t_media",
            "t_media_process_task",
            "t_device"
    };

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.migrate();
            verifyRequiredTables(flyway);
        };
    }

    private void verifyRequiredTables(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<String> missingTables = new ArrayList<>();
            for (String table : REQUIRED_TABLES) {
                if (!tableExists(metaData, catalog, table)) {
                    missingTables.add(table);
                }
            }
            if (!missingTables.isEmpty()) {
                throw new IllegalStateException("Database schema is incomplete after Flyway migration. Missing tables: "
                        + String.join(", ", missingTables));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify database schema after Flyway migration", ex);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String table) throws Exception {
        try (ResultSet resultSet = metaData.getTables(catalog, null, table, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(catalog, null, table.toUpperCase(), new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
