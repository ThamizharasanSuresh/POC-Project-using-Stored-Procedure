package com.storedprocedure.service;

import com.storedprocedure.DbUtils;
import com.storedprocedure.dto.TrinoResponseBean;
import com.storedprocedure.trino.TrinoRestExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Service
public class TrinoBatchService {

    @Value("${app.target-db}")
    private String targetDb;

    private final TrinoRestExecutor trinoExecutor;
    private final StoredProcBatchInsertService insertService;
    private final DataSource dataSource;

    public TrinoBatchService(
            TrinoRestExecutor trinoExecutor,
            StoredProcBatchInsertService insertService,
            DataSource dataSource
    ) {
        this.trinoExecutor = trinoExecutor;
        this.insertService = insertService;
        this.dataSource = dataSource;
    }

    public String runBatchQuery(String query) throws Exception {

        Connection conn = null;
        int totalRows = 0;
        int batchNo = 1;
        String tableName = extractTableName(query);
        boolean tableCreated = false;

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            JdbcTemplate txJdbc = new JdbcTemplate(
                    new SingleConnectionDataSource(conn, true)
            );

            TrinoResponseBean resp = trinoExecutor.executeInitial(query);

            while ((resp.getColumns() == null || resp.getColumns().isEmpty())
                    && resp.getNextUri() != null) {
                resp = trinoExecutor.getNextPage(resp.getNextUri());
            }

            DbUtils.createTable(txJdbc, tableName, resp.getColumns(), targetDb);
            tableCreated = true;

            while (true) {

                if (resp.getData() != null && !resp.getData().isEmpty()) {

                    String columns = buildColumnBlock(resp);
                    String values = buildValuesBlock(resp);

                    if ("oracle".equalsIgnoreCase(targetDb) && values.length() > 20000) {

                        List<String> chunks = DbUtils.splitBySqlSize(values, 2000);

                        for (String chunk : chunks) {

                            insertService.insertBatch(
                                    tableName, columns, chunk, targetDb
                            );

                            int rows = countRowsInValues(chunk);
                            totalRows += rows;

                            System.out.println("Batch " + batchNo++ + " | Rows inserted = " + rows);
                        }

                    } else {

                        insertService.insertBatch(
                                tableName, columns, values, targetDb
                        );

                        int rows = resp.getData().size();
                        totalRows += rows;

                        System.out.println("Batch " + batchNo++ + " | Rows inserted = " + rows);
                    }
                }

                if (resp.getNextUri() == null ||
                        "FINISHED".equalsIgnoreCase(resp.getStats().getState())) {
                    break;
                }

                resp = trinoExecutor.getNextPage(resp.getNextUri());
            }

            conn.commit();
            return "FINISHED. Rows inserted = " + totalRows;

        } catch (Exception ex) {

            if (conn != null) {
                conn.rollback();
            }

            if (tableCreated) {
                DbUtils.dropTableQuietly(
                        new JdbcTemplate(dataSource),
                        tableName,
                        targetDb
                );
            }
            System.out.println(ex.getMessage());
        } finally {

            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return tableName;
    }


    private String buildColumnBlock(TrinoResponseBean resp) {
        return resp.getColumns().stream()
                .map(c -> quoteColumn(c.get("name").toString()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String buildValuesBlock(TrinoResponseBean resp) {

        StringBuilder sb = new StringBuilder();

        for (var row : resp.getData()) {
            sb.append("(");
            for (int i = 0; i < row.size(); i++) {

                Object v = row.get(i);

                if ("oracle".equalsIgnoreCase(targetDb)) {
                    sb.append(toOracleSqlValue(v));
                } else {
                    sb.append(toSqlValue(v));
                }

                if (i < row.size() - 1) sb.append(",");
            }
            sb.append("),");
        }

        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static int countRowsInValues(String valuesBlock) {
        if (valuesBlock == null || valuesBlock.isEmpty()) return 0;
        return valuesBlock.split("\\),\\(").length;
    }

    private String toOracleSqlValue(Object v) {

        if (v == null) return "NULL";

        if (v instanceof Boolean b) {
            return b ? "1" : "0";
        }

        if (v instanceof Number) {
            return v.toString();
        }

        String s = v.toString().replace("'", "''");

        if (s.matches("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")) {
            return "TIMESTAMP '" + s.replace("T", " ") + "'";
        }

        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "DATE '" + s + "'";
        }

        return "'" + s + "'";
    }

    private String toSqlValue(Object v) {

        if (v == null) return "NULL";

        if (v instanceof Boolean b) {
            if ("postgres".equalsIgnoreCase(targetDb)) {
                return b ? "TRUE" : "FALSE";
            }
            return b ? "1" : "0";
        }

        if (v instanceof Number) {
            return v.toString();
        }

        return "'" + v.toString().replace("'", "''") + "'";
    }

    private String quoteColumn(String col) {
        return switch (targetDb.toLowerCase()) {
            case "mysql" -> "`" + col + "`";
            case "oracle" -> "\"" + col.toUpperCase() + "\"";
            default -> "\"" + col + "\"";
        };
    }

    private String extractTableName(String query) {
        String q = query.replaceAll("\\s+", " ").trim();
        String from = q.substring(q.toLowerCase().indexOf(" from ") + 6);
        return from.split("\\s+")[0].split("\\.")[from.split("\\.").length - 1];
    }
}
