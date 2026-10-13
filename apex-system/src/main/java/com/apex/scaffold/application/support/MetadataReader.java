package com.apex.scaffold.application.support;

import com.apex.scaffold.application.dto.admin.ColumnInfoDTO;
import com.apex.scaffold.application.dto.admin.TableInfoDTO;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 数据库元数据读取器
 *
 * <p>通过 JDBC {@link DatabaseMetaData} 读取数据库表结构、列信息等元数据，
 * 用于代码生成时获取表和列的详细信息。所有 JDBC 操作在 {@link BlockingIoSchedulers#jdbc()} 调度器上执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class MetadataReader {

    private static final String DB_TYPE_POSTGRESQL = "postgresql";

    /**
     * 读取数据库中的所有表名
     *
     * @param dataSource 数据源
     * @param dbType 数据库类型（mysql/postgresql）
     * @return 表名列表
     */
    public Flux<String> getTables(DataSource dataSource, String dbType) {
        return Mono.fromCallable(() -> {
                    List<String> tables = new ArrayList<>(16);
                    try (Connection conn = dataSource.getConnection()) {
                        DatabaseMetaData metaData = conn.getMetaData();
                        String catalog = conn.getCatalog();
                        String schema = getSchema(conn, dbType);

                        try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[] {"TABLE"})) {
                            while (rs.next()) {
                                String tableName = rs.getString("TABLE_NAME");
                                tables.add(tableName);
                            }
                        }
                    }
                    return tables;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .doOnError(e -> log.error("读取数据库表列表失败", e))
                .onErrorMap(e -> e instanceof BusinessException
                        ? e
                        : new BusinessException(ResultCode.DB_ERROR, "读取数据库表列表失败: " + e.getMessage()));
    }

    /**
     * 读取指定表的列信息
     *
     * @param dataSource 数据源
     * @param dbType 数据库类型（mysql/postgresql）
     * @param tableName 表名
     * @return 表信息（包含列列表）
     */
    public Mono<TableInfoDTO> getTableColumns(DataSource dataSource, String dbType, String tableName) {
        return Mono.fromCallable(() -> {
                    try (Connection conn = dataSource.getConnection()) {
                        DatabaseMetaData metaData = conn.getMetaData();
                        String catalog = conn.getCatalog();
                        String schema = getSchema(conn, dbType);

                        TableInfoDTO.TableInfoDTOBuilder tableBuilder =
                                TableInfoDTO.builder().tableName(tableName).tableType("TABLE");

                        try (ResultSet tableRs =
                                metaData.getTables(catalog, schema, tableName, new String[] {"TABLE"})) {
                            if (tableRs.next()) {
                                tableBuilder.tableComment(tableRs.getString("REMARKS"));
                            }
                        }

                        List<ColumnInfoDTO> columns = new ArrayList<>(16);
                        try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                            List<String> primaryKeys = new ArrayList<>(16);
                            while (pkRs.next()) {
                                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                            }

                            try (ResultSet columnRs = metaData.getColumns(catalog, schema, tableName, "%")) {
                                while (columnRs.next()) {
                                    String columnName = columnRs.getString("COLUMN_NAME");
                                    String dataType = columnRs.getString("TYPE_NAME");
                                    String javaType = TypeMapper.getJavaType(dbType, dataType);
                                    String javaFieldName = TypeMapper.toCamelCase(columnName);
                                    String columnComment = columnRs.getString("REMARKS");
                                    boolean isNullable = "YES".equalsIgnoreCase(columnRs.getString("IS_NULLABLE"));
                                    int columnLength = columnRs.getInt("COLUMN_SIZE");
                                    String defaultValue = columnRs.getString("COLUMN_DEF");

                                    ColumnInfoDTO column = ColumnInfoDTO.builder()
                                            .columnName(columnName)
                                            .dataType(dataType)
                                            .javaType(javaType)
                                            .javaFieldName(javaFieldName)
                                            .columnComment(columnComment)
                                            .isPrimaryKey(primaryKeys.contains(columnName))
                                            .isNullable(isNullable)
                                            .columnLength(columnLength)
                                            .defaultValue(defaultValue)
                                            .build();
                                    columns.add(column);
                                }
                            }
                        }

                        tableBuilder.columns(columns);
                        return tableBuilder.build();
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnError(e -> log.error("读取表 {} 的列信息失败", tableName, e))
                .onErrorMap(e -> e instanceof BusinessException
                        ? e
                        : new BusinessException(ResultCode.DB_ERROR, "读取表列信息失败: " + e.getMessage()));
    }

    /**
     * 获取数据库 schema
     *
     * @param conn 数据库连接
     * @param dbType 数据库类型
     * @return schema 名称
     */
    private String getSchema(Connection conn, String dbType) throws SQLException {
        if (DB_TYPE_POSTGRESQL.equalsIgnoreCase(dbType)) {
            return "public";
        }
        return conn.getSchema();
    }
}
