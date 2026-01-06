package com.storedprocedure.service;

import com.storedprocedure.DbUtils;
import com.storedprocedure.dto.TrinoResponseBean;
import com.storedprocedure.trino.TrinoRestExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TrinoBatchService {

    private static final List<String> EXCLUDED_COLUMNS =
            List.of("ads_id", "ads_ing_sid");

    @Value("${app.target-db}")
    private String targetDb;

    private final TrinoRestExecutor trinoExecutor;
    private final BatchInsertExecutorResolver executorResolver;
    private final DataSource dataSource;

    public TrinoBatchService(
            TrinoRestExecutor trinoExecutor,
            BatchInsertExecutorResolver executorResolver,
            DataSource dataSource
    ) {
        this.trinoExecutor = trinoExecutor;
        this.executorResolver = executorResolver;
        this.dataSource = dataSource;
    }

    public String runBatchQuery(String query) throws Exception {

        Instant jobStart = Instant.now();

        Connection conn = null;
        int totalRows = 0;
        int fetchBatchNo = 0;
        int insertBatchNo = 1;

        String tableName = extractTableName(query);
        boolean tableCreated = false;

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            JdbcTemplate txJdbc =
                    new JdbcTemplate(new SingleConnectionDataSource(conn, true));

            TrinoResponseBean resp = trinoExecutor.executeInitial(query);

            while ((resp.getColumns() == null || resp.getColumns().isEmpty())
                    && resp.getNextUri() != null) {

                Instant fetchStart = Instant.now();
                resp = trinoExecutor.getNextPage(resp.getNextUri());
                Instant fetchEnd = Instant.now();
                log("TRINO FETCH", fetchBatchNo++, fetchStart, fetchEnd,
                        resp.getData() == null ? 0 : resp.getData().size());
            }

            List<Integer> includedIndexes = filterColumns(resp);

            TrinoResponseBean finalResp = resp;
            List<Map<String, Object>> filteredColumns =
                    includedIndexes.stream()
                            .map(i -> finalResp.getColumns().get(i))
                            .toList();

            DbUtils.createTable(txJdbc, tableName, filteredColumns, targetDb);
            tableCreated = true;

            while (true) {

                if (resp.getData() != null && !resp.getData().isEmpty()) {

                    String columns = buildColumnBlock(resp, includedIndexes);
                    String values  = buildValuesBlock(resp, includedIndexes);

                    Instant insertStart = Instant.now();

                    executorResolver
                            .resolve(targetDb)
                            .insertBatch(txJdbc, tableName, columns, values);

                    Instant insertEnd = Instant.now();

                    int rows = resp.getData().size();
                    totalRows += rows;

                    log("DB INSERT", insertBatchNo++, insertStart, insertEnd, rows);
                }

                if (resp.getNextUri() == null ||
                        "FINISHED".equalsIgnoreCase(resp.getStats().getState())) {
                    break;
                }

                Instant fetchStart = Instant.now();
                resp = trinoExecutor.getNextPage(resp.getNextUri());
                Instant fetchEnd = Instant.now();

                log("TRINO FETCH", fetchBatchNo++,
                        fetchStart, fetchEnd,
                        resp.getData() == null ? 0 : resp.getData().size());
            }

            conn.commit();

            Instant jobEnd = Instant.now();
            Duration total = Duration.between(jobStart, jobEnd);

            System.out.println("Job Start   : " + format(jobStart));
            System.out.println("Job End     : " + format(jobEnd));
            System.out.println("Total Time  : " +
                    total.toMinutes() + " min " +
                    total.minusMinutes(total.toMinutes()).getSeconds() + " sec");
            System.out.println("Total Rows  : " + totalRows);
            return "FINISHED. Rows inserted = " + totalRows;

        } catch (Exception ex) {

            if (conn != null) conn.rollback();

            if (tableCreated) {
                DbUtils.dropTableQuietly(
                        new JdbcTemplate(dataSource),
                        tableName,
                        targetDb
                );
            }

            throw ex;

        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    private void log(
            String label,
            int batch,
            Instant start,
            Instant end,
            int rows
    ) {
        System.out.println(
                label + " Batch " + batch +
                        " | Start = " + format(start) +
                        " | End = " + format(end) +
                        " | Time = " +
                        Duration.between(start, end).toMillis() + " ms" +
                        " | Rows = " + rows
        );
    }

    private String buildColumnBlock(
            TrinoResponseBean resp,
            List<Integer> includedIndexes
    ) {
        return includedIndexes.stream()
                .map(i -> quoteColumn(
                        resp.getColumns().get(i).get("name").toString()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String buildValuesBlock(
            TrinoResponseBean resp,
            List<Integer> includedIndexes
    ) {
        StringBuilder sb = new StringBuilder();

        for (var row : resp.getData()) {
            sb.append("(");
            for (int j = 0; j < includedIndexes.size(); j++) {

                Object v = row.get(includedIndexes.get(j));

                if ("oracle".equalsIgnoreCase(targetDb)) {
                    sb.append(toOracleSqlValue(v));
                } else {
                    sb.append(toSqlValue(v));
                }

                if (j < includedIndexes.size() - 1) sb.append(",");
            }
            sb.append("),");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private List<Integer> filterColumns(TrinoResponseBean resp) {

        List<Integer> included = new ArrayList<>();

        for (int i = 0; i < resp.getColumns().size(); i++) {
            String col =
                    resp.getColumns().get(i).get("name").toString().toLowerCase();
            if (!EXCLUDED_COLUMNS.contains(col)) included.add(i);
        }

        return included;
    }

    private String toOracleSqlValue(Object v) {

        if (v == null) return "NULL";

        if (v instanceof Boolean b) return b ? "1" : "0";

        if (v instanceof Number) return v.toString();

        String s = v.toString().replace("'", "''");

        if (s.matches("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?"))
            return "TIMESTAMP '" + s.replace("T", " ") + "'";

        if (s.matches("\\d{4}-\\d{2}-\\d{2}"))
            return "DATE '" + s + "'";

        return "'" + s + "'";
    }

    private String toSqlValue(Object v) {

        if (v == null) return "NULL";

        if (v instanceof Boolean b) {
            if ("postgres".equalsIgnoreCase(targetDb))
                return b ? "TRUE" : "FALSE";
            return b ? "1" : "0";
        }

        if (v instanceof Number) return v.toString();

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
        return from.split("\\s+")[0]
                .split("\\.")[from.split("\\.").length - 1];
    }

    private String format(Instant t) {
        return DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(t);
    }
}
