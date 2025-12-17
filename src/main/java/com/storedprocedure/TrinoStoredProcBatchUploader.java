//package com.storedprocedure;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.storedprocedure.dto.TrinoResponseBean;
//import com.storedprocedure.trino.TrinoRestExecutor;
//import com.storedprocedure.service.StoredProcBatchInsertService;
//
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import javax.sql.DataSource;
//import java.util.*;
//
//@Component
//public class TrinoStoredProcBatchUploader implements CommandLineRunner {
//
//    @Value("${app.trino-query}")
//    private String trinoQuery;
//
//    @Value("${app.target-db}")
//    private String targetDb;
//
//    private final TrinoRestExecutor trinoRestExecutor;
//    private final StoredProcBatchInsertService batchInsertService;
//
//    public TrinoStoredProcBatchUploader(
//            TrinoRestExecutor trinoRestExecutor,
//            StoredProcBatchInsertService batchInsertService
//    ) {
//        this.trinoRestExecutor = trinoRestExecutor;
//        this.batchInsertService = batchInsertService;
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//
//        System.out.println("\n=== TRINO REST → STORED PROCEDURE BATCH LOADER ===");
//
//        String table = "trino_table";
//
//        /** 1️⃣ POST Query to Trino */
//        TrinoResponseBean resp = trinoRestExecutor.executeInitial(trinoQuery);
//
//        if (resp.getData() != null && !resp.getData().isEmpty()) {
//            insertIntoDB(resp, table);
//        }
//
//        /** 2️⃣ Follow nextUri pages */
//        while (resp.getNextUri() != null
//                && !resp.getStats().getState().equalsIgnoreCase("FINISHED")) {
//
//            resp = trinoRestExecutor.getNextPage(resp.getNextUri());
//
//            if (resp.getData() != null && !resp.getData().isEmpty()) {
//                insertIntoDB(resp, table);
//            }
//        }
//
//        System.out.println("✔ All data inserted successfully!");
//    }
//
//    private void insertIntoDB(TrinoResponseBean resp, String table) {
//
//        List<Map<String, String>> cols = resp.getColumns();
//
//        List<String> colNames = cols.stream()
//                .map(c -> c.get("name"))
//                .toList();
//
//        String columns = String.join(",", colNames);
//
//        StringBuilder valuesBlock = new StringBuilder();
//
//        for (List<Object> row : resp.getData()) {
//            valuesBlock.append("(");
//
//            for (int i = 0; i < row.size(); i++) {
//                Object v = row.get(i);
//
//                if (v == null) valuesBlock.append("NULL");
//                else valuesBlock.append("'").append(v.toString().replace("'", "''")).append("'");
//
//                if (i < row.size() - 1) valuesBlock.append(",");
//            }
//
//            valuesBlock.append("),");
//        }
//
//        // remove last comma
//        valuesBlock.setLength(valuesBlock.length() - 1);
//
//        System.out.println("→ Inserting batch: " + resp.getData().size());
//
//        batchInsertService.insertBatch(table, columns, valuesBlock.toString(), targetDb);
//    }
//}
