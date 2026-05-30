package com.cloudalbum.publisher.distribution.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributionDisplayOverrideSchemaRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            ensureColumn(connection, databaseProductName, "transition_style", "VARCHAR(20)", "播放转场覆盖，空表示继承相册");
            ensureColumn(connection, databaseProductName, "display_style", "VARCHAR(20)", "展示布局覆盖，空表示继承相册");
            ensureColumn(connection, databaseProductName, "display_variant", "VARCHAR(32)", "展示布局子样式覆盖，空表示继承相册");
            ensureColumn(connection, databaseProductName, "show_time_and_date", "TINYINT", "是否显示时间日期覆盖，空表示继承相册");
        }
    }

    private void ensureColumn(Connection connection,
                              String databaseProductName,
                              String columnName,
                              String columnType,
                              String columnComment) throws SQLException {
        if (columnExists(connection.getMetaData(), columnName)) {
            return;
        }
        jdbcTemplate.execute(buildAddColumnSql(databaseProductName, columnName, columnType, columnComment));
        log.info("Added missing distribution display override column: {}", columnName);
    }

    private boolean columnExists(DatabaseMetaData metaData, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, "t_distribution", columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = metaData.getColumns(null, null, "T_DISTRIBUTION", columnName.toUpperCase(Locale.ROOT))) {
            return columns.next();
        }
    }

    private String buildAddColumnSql(String databaseProductName, String columnName, String columnType, String columnComment) {
        if (databaseProductName != null && databaseProductName.toLowerCase(Locale.ROOT).contains("mysql")) {
            return "ALTER TABLE t_distribution ADD COLUMN " + columnName + " " + columnType + " NULL COMMENT '" + columnComment + "'";
        }
        return "ALTER TABLE t_distribution ADD COLUMN " + columnName + " " + columnType;
    }
}
