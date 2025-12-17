//package com.storedprocedure;
//
//import com.opencsv.CSVReader;
//import com.opencsv.CSVReaderBuilder;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import javax.sql.DataSource;
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileReader;
//import java.sql.*;
//import java.util.*;
//
//
//@Component
//public class CSVStoredProcBatchUploader implements CommandLineRunner {
//
//    @Value("${app.input-file}")
//    private String csvPath;
//
//    @Value("${app.batch-size:500}")
//    private int batchSize;
//
//    @Value("${app.target-db}")
//    private String targetDb;
//
//    private final JdbcTemplate jdbc;
//    private final DataSource ds;
//
//    public CSVStoredProcBatchUploader(JdbcTemplate targetJdbcTemplate, DataSource targetDataSource) {
//        this.jdbc = targetJdbcTemplate;
//        this.ds = targetDataSource;
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//
//        long startTotal = System.currentTimeMillis();
//        File file = new File(csvPath);
//        if (!file.exists()) {
//            System.err.println("CSV NOT FOUND: " + csvPath);
//            return;
//        }
//        System.out.println("Using Target DB: " + targetDb);
//        BufferedReader br = new BufferedReader(new FileReader(file), 8 * 1024 * 1024);
//        try (CSVReader reader = new CSVReaderBuilder(br).build()) {
//            String[] header = reader.readNext();
//            if (header == null) {
//                System.out.println("CSV is empty.");
//                return;
//            }
//            header[0] = header[0].replace("\uFEFF", "").trim();
//            String table = DbUtils.sanitizeTable(file.getName().replaceFirst("\\..*$", "") + "_table");
//            System.out.println("TABLE NAME = " + table);
//            boolean initialized = DbUtils.isTableInitialized(jdbc, table);
//            if (initialized) {
//                System.out.println("Table '" + table + "' already initialized. Skipping ALL checks.");
//            } else {
//                System.out.println("Table '" + table + "' NOT initialized. Running pre-checks NOW.");
//            }
//            boolean tableExists = DbUtils.tableExists(jdbc, table);
//            String[] sanitizedCols = Arrays.stream(header)
//                    .map(DbUtils::sanitizeColumn)
//                    .toArray(String[]::new);
//            if (!initialized) {
//                if (!tableExists) {
//                    System.out.println("Table does NOT exist. Creating table...");
//                    DbUtils.createTable(jdbc, table, header, targetDb);
//                    tableExists = true;
//                } else {
//                    System.out.println("Table exists. Validating unique constraint...");
//                }
//                DbUtils.ensureUniqueConstraintOnFirstColumn(jdbc, table, sanitizedCols[0], targetDb);
//                List<String> existingCols = DbUtils.getTableColumns(jdbc, table);
//                List<String> missing = DbUtils.findMissingColumns(existingCols, sanitizedCols);
//                if (!missing.isEmpty()) {
//                    System.out.println("Missing DB columns: " + missing);
//                    System.out.println("These columns will be ignored.");
//                }
//            } else {
//                System.out.println("Skipping column + constraint validation (already initialized).");
//            }
//            Set<String> existingKeys = new HashSet<>();
//            if (!initialized && tableExists) {
//                System.out.println("Loading existing first-column keys...");
//                existingKeys = DbUtils.fetchExistingFirstColumnKeys(jdbc, table, sanitizedCols[0]);
//                System.out.println("Existing key count = " + existingKeys.size());
//            } else {
//                System.out.println("Skipping loading existing keys (already initialized).");
//            }
//            List<String[]> batchRows = new ArrayList<>();
//            List<String> skippedPreExisting = new ArrayList<>();
//            int totalInserted = 0;
//            boolean markedInitializedThisRun = initialized;
//            String[] row;
//            while ((row = reader.readNext()) != null) {
//                if (row.length != sanitizedCols.length) {
//                    row = Arrays.copyOf(row, sanitizedCols.length);
//                }
//                String key = row[0];
//                if (!markedInitializedThisRun && existingKeys.contains(key)) {
//                    skippedPreExisting.add(key);
//                    continue;
//                }
//                if (!markedInitializedThisRun) existingKeys.add(key);
//                batchRows.add(row);
//                if (batchRows.size() >= batchSize) {
//                    try {
//                        int inserted = executeBatchInsert(table, sanitizedCols, batchRows);
//                        totalInserted += inserted;
//                        if (!markedInitializedThisRun) {
//                            DbUtils.markTableInitialized(jdbc, table);
//                            markedInitializedThisRun = true;
//                            existingKeys = new HashSet<>();
//                            System.out.println("Table '" + table + "' marked as initialized (first successful batch).");
//                        }
//                    } catch (Exception e) {
//                        System.err.println("Batch insert failed for batch starting with key '" +
//                                (batchRows.isEmpty() ? "unknown" : batchRows.get(0)[0]) + "': " + e.getMessage());
//                        e.printStackTrace();
//                    }
//                    batchRows.clear();
//                }
//            }
//            System.out.println("TOTAL INSERTED ROWS = " + totalInserted);
//            if (!skippedPreExisting.isEmpty()) System.out.println("SKIPPED (pre-existing keys) = " + skippedPreExisting.size());
//        }
//        long endTotal = System.currentTimeMillis();
//        long totalms = endTotal - startTotal;
//        long minutes = totalms / 60000;
//        double seconds = (totalms % 60000) / 1000.0;
//        System.out.println("TOTAL TIME: " + minutes + " min " + seconds + " sec");
//    }
//
//    private int executeBatchInsert(String table, String[] cols, List<String[]> rows) throws Exception {
//        if (targetDb.equalsIgnoreCase("oracle")) {
//            try (Connection conn = ds.getConnection()) {
//                cols = filterOracleColumns(conn, table, cols);
//                rows = trimRowsToColumns(cols, rows);
//            }
//        }
//        long batchStart = System.currentTimeMillis();
//        String valuesBlock = buildValuesBlock(rows);
//        try (Connection conn = ds.getConnection()) {
//            switch (targetDb.toLowerCase()) {
//                case "postgres" -> {
//                    callPostgres(conn, table, cols, valuesBlock);
//                    long batchEnd = System.currentTimeMillis();
//                    System.out.println("Inserted " + rows.size() + " Postgres in " + (batchEnd - batchStart) + " ms");
//                    return rows.size();
//                }
//                case "mysql" -> {
//                    callMySql(conn, table, cols, valuesBlock);
//                    long batchEnd = System.currentTimeMillis();
//                    System.out.println("Inserted " + rows.size() + " MySQL in " + (batchEnd - batchStart) + " ms");
//                    return rows.size();
//                }
//                case "oracle" -> {
//                    callOracle(conn, table, cols, valuesBlock);
//                    long batchEnd = System.currentTimeMillis();
//                    System.out.println("Inserted " + rows.size() + " Oracle in " + (batchEnd - batchStart) + " ms");
//                    return rows.size();
//                }
//                default -> throw new RuntimeException("Unknown DB: " + targetDb);
//            }
//        }
//    }
//
//    private static String buildValuesBlock(List<String[]> rows) {
//        StringBuilder sb = new StringBuilder(rows.size() * 200);
//
//        for (int i = 0; i < rows.size(); i++) {
//            String[] r = rows.get(i);
//            sb.append("(");
//            for (int j = 0; j < r.length; j++) {
//                String v = r[j];
//                if (v == null || v.isEmpty()) {
//                    sb.append("NULL");
//                } else {
//                    String esc = v.replace("'", "''");
//                    sb.append("'").append(esc).append("'");
//                }
//                if (j < r.length - 1) sb.append(",");
//            }
//            sb.append(")");
//            if (i < rows.size() - 1) sb.append(",");
//        }
//        return sb.toString();
//    }
//
//    private void callMySql(Connection conn, String table, String[] cols, String valuesBlock) throws SQLException {
//
//        String sql = "{ call insert_csv_batch_mysql(?, ?, ?) }";
//        String pColumns = String.join(", ", wrapColumnsForInsert(cols, "mysql"));
//        try (CallableStatement cs = conn.prepareCall(sql)) {
//            cs.setString(1, table);
//            cs.setString(2, pColumns);
//            cs.setString(3, valuesBlock);
//            cs.execute();
//        }
//    }
//
//    private void callPostgres(Connection conn, String table, String[] cols, String valuesBlock) throws SQLException {
//
//        String sql = "CALL insert_csv_batch_pg(?, ?, ?)";
//        String pColumns = String.join(", ", wrapColumnsForInsert(cols, "postgres"));
//        try (CallableStatement cs = conn.prepareCall(sql)) {
//            cs.setString(1, table);
//            cs.setString(2, pColumns);
//            cs.setString(3, valuesBlock);
//            cs.execute();
//        }
//    }
//
//
//    private void callOracle(Connection conn, String table, String[] cols, String valuesBlock) throws Exception {
//
//        String sql = "{ call insert_csv_batch_oracle(?, ?, ?) }";
//        String pColumns = String.join(", ", wrapColumnsForInsert(cols, "oracle"));
//        try (CallableStatement cs = conn.prepareCall(sql)) {
//            cs.setString(1, table);
//            cs.setString(2, pColumns);
//            cs.setString(3, valuesBlock);
//            cs.execute();
//        }
//    }
//
//    private static String[] wrapColumnsForInsert(String[] cols, String dbType) {
//        String[] out = new String[cols.length];
//        for (int i = 0; i < cols.length; i++) {
//            if (dbType.equalsIgnoreCase("mysql")) out[i] = "`" + cols[i] + "`";
//            else if (dbType.equalsIgnoreCase("postgres")) out[i] = "\"" + cols[i] + "\"";
//            else out[i] = cols[i];
//        }
//        return out;
//    }
//
//
//    private String[] filterOracleColumns(Connection conn, String table, String[] cols) throws SQLException {
//
//        Set<String> oracleCols = new HashSet<>();
//        try (PreparedStatement ps = conn.prepareStatement(
//                "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?"
//        )) {
//            ps.setString(1, table.toUpperCase());
//            ResultSet rs = ps.executeQuery();
//            while (rs.next()) {
//                oracleCols.add(rs.getString(1).toLowerCase());
//            }
//        }
//        List<String> finalCols = new ArrayList<>();
//        for (String c : cols) {
//            if (oracleCols.contains(c.toLowerCase())) {
//                finalCols.add(c);
//            } else {
//                System.out.println("Skipping missing Oracle column: " + c);
//            }
//        }
//        return finalCols.toArray(new String[0]);
//    }
//
//    private List<String[]> trimRowsToColumns(String[] cols, List<String[]> rows) {
//        int newLen = cols.length;
//        List<String[]> out = new ArrayList<>();
//        for (String[] r : rows) {
//            out.add(Arrays.copyOf(r, newLen));
//        }
//        return out;
//    }
//}
