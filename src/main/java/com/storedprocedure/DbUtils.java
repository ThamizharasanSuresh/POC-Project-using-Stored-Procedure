package com.storedprocedure;

import com.storedprocedure.dto.TrinoResponseBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DbUtils {

    private static final Set<String> RESERVED = Set.of(
            "index","order","date","number","user","select","from","where",
            "table","group","view","primary","foreign","check","insert","delete"
    );

    public static String sanitizeColumn(String name) {
        String col = name.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        if (!col.matches("^[a-zA-Z].*")) col = "COL_" + col;
        if (RESERVED.contains(col.toLowerCase())) col = col + "_COL";
        return col;
    }

    public static void createTable(
            JdbcTemplate jdbc,
            String table,
            List<Map<String, Object>> trinoColumns,
            String dbType
    ) {
        switch (dbType.toLowerCase()) {
            case "postgres" -> createPostgres(jdbc, table, trinoColumns);
            case "mysql"    -> createMySQL(jdbc, table, trinoColumns);
            case "oracle"   -> createOracle(jdbc, table, trinoColumns);
            default -> throw new IllegalArgumentException("Unsupported DB: " + dbType);
        }
    }

    private static void createPostgres(
            JdbcTemplate jdbc,
            String table,
            List<Map<String, Object>> columns
    ) {
        StringBuilder sql = new StringBuilder(
                "CREATE TABLE IF NOT EXISTS \"" + table + "\" ("
        );
        for (int i = 0; i < columns.size(); i++) {
            String colName = sanitizeColumn(columns.get(i).get("name").toString());
            String trinoType = columns.get(i).get("type").toString();
            sql.append("\"")
                    .append(colName)
                    .append("\" ")
                    .append(mapType(trinoType, "postgres"));

            if (i < columns.size() - 1) sql.append(", ");
        }
        sql.append(")");
        jdbc.execute(sql.toString());
    }

    private static void createMySQL(
            JdbcTemplate jdbc,
            String table,
            List<Map<String, Object>> columns
    ) {
        StringBuilder sql = new StringBuilder(
                "CREATE TABLE IF NOT EXISTS `" + table + "` ("
        );
        for (int i = 0; i < columns.size(); i++) {
            String colName = sanitizeColumn(columns.get(i).get("name").toString());
            String trinoType = columns.get(i).get("type").toString();
            sql.append("`")
                    .append(colName)
                    .append("` ")
                    .append(mapType(trinoType, "mysql"));

            if (i < columns.size() - 1) sql.append(", ");
        }
        sql.append(")");
        jdbc.execute(sql.toString());
    }

    private static void createOracle(
            JdbcTemplate jdbc,
            String table,
            List<Map<String, Object>> columns
    ) {
        String tableName = table.toUpperCase();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        if (exists != null && exists > 0) return;
        StringBuilder sql = new StringBuilder(
                "CREATE TABLE " + tableName + " ("
        );
        for (int i = 0; i < columns.size(); i++) {
            String colName = sanitizeColumn(columns.get(i).get("name").toString()).toUpperCase();
            String trinoType = columns.get(i).get("type").toString();
            sql.append(colName)
                    .append(" ")
                    .append(mapType(trinoType, "oracle"));

            if (i < columns.size() - 1) sql.append(", ");
        }
        sql.append(")");
        jdbc.execute(sql.toString());
    }

    private static String mapType(String trinoType, String db) {
        String t = trinoType.toLowerCase();
        switch (db) {
            case "postgres":
                if (t.startsWith("varchar") || t.startsWith("char")) return "TEXT";
                if (t.startsWith("integer")) return "INTEGER";
                if (t.startsWith("bigint")) return "BIGINT";
                if (t.startsWith("double") || t.startsWith("real")) return "DOUBLE PRECISION";
                if (t.startsWith("boolean")) return "BOOLEAN";
                if (t.startsWith("date")) return "DATE";
                if (t.startsWith("timestamp")) return "TIMESTAMP";
                if (t.startsWith("decimal")) return "NUMERIC";
                return "TEXT";

            case "mysql":
                if (t.startsWith("varchar") || t.startsWith("char")) return "LONGTEXT";
                if (t.startsWith("integer")) return "INT";
                if (t.startsWith("bigint")) return "BIGINT";
                if (t.startsWith("double") || t.startsWith("real")) return "DOUBLE";
                if (t.startsWith("boolean")) return "BOOLEAN";
                if (t.startsWith("date")) return "DATE";
                if (t.startsWith("timestamp")) return "DATETIME";
                if (t.startsWith("decimal")) return "DECIMAL(38,10)";
                return "LONGTEXT";

            case "oracle":
                if (t.startsWith("varchar") || t.startsWith("char")) return "VARCHAR2(4000)";
                if (t.startsWith("integer")) return "NUMBER(10)";
                if (t.startsWith("bigint")) return "NUMBER(19)";
                if (t.startsWith("double") || t.startsWith("real")) return "BINARY_DOUBLE";
                if (t.startsWith("boolean")) return "NUMBER(1)";
                if (t.startsWith("date")) return "DATE";
                if (t.startsWith("timestamp")) return "TIMESTAMP";
                if (t.startsWith("decimal")) return "NUMBER(38,10)";
                return "CLOB";

            default:
                return "TEXT";
        }
    }
    public static List<String> splitBySqlSize(String valuesBlock, int maxLen) {

        final int ORACLE_DYNAMIC_SQL_LIMIT = 32767;

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        String[] rows = valuesBlock.split("\\),\\(");

        for (String row : rows) {

            String normalized =
                    (row.startsWith("(") ? row : "(" + row) +
                            (row.endsWith(")") ? "" : ")");

            if (normalized.length() >= maxLen) {

                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }

                if (normalized.length() > ORACLE_DYNAMIC_SQL_LIMIT) {
                    throw new IllegalStateException(
                            "Row exceeds Oracle hard SQL limit (32K). Length=" +
                                    normalized.length()
                    );
                }

                chunks.add(normalized);
                continue;
            }

            if (current.length() == 0) {
                current.append(normalized);
            }
            else if (current.length() + normalized.length() + 1 < maxLen) {
                current.append(",").append(normalized);
            }
            else {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(normalized);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }



    public static void dropTableQuietly(
            JdbcTemplate jdbc,
            String table,
            String dbType
    ) {
        try {
            switch (dbType.toLowerCase()) {
                case "postgres" ->
                        jdbc.execute("DROP TABLE IF EXISTS \"" + table + "\"");

                case "mysql" ->
                        jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");

                case "oracle" ->
                        jdbc.execute("DROP TABLE " + table.toUpperCase() + " PURGE");
            }
        } catch (Exception e) {
            System.out.println("Error dropping table " + table + ": " + e.getMessage());
        }
    }
}
