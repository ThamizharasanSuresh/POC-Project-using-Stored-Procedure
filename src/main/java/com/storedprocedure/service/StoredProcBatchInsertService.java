//package com.storedprocedure.service;
//
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Service;
//
//@Service
//public class StoredProcBatchInsertService {
//
//    private final JdbcTemplate jdbc;
//
//    public StoredProcBatchInsertService(JdbcTemplate jdbc) {
//        this.jdbc = jdbc;
//    }
//
//    public void insertBatch(
//            String table,
//            String columns,
//            Object valuesBlock,
//            String dbType
//    ) {
//
//        System.out.println("Executing Stored Procedure for DB: " + dbType);
//
//        switch (dbType.toLowerCase()) {
//
//            case "mysql" -> jdbc.update(
//                    "CALL insert_csv_batch_mysql(?, ?, ?)",
//                    table, columns, valuesBlock
//            );
//
//            case "postgres" -> jdbc.update(
//                    "CALL insert_csv_batch_pg(?, ?, ?)",
//                    table, columns, valuesBlock
//            );
//
//            case "oracle" -> jdbc.update(
//                    "CALL STORED_PROCEDURE.insert_csv_batch_oracle(?, ?, ?)",
//                    table, columns, valuesBlock
//            );
//
//            default -> throw new IllegalArgumentException("Unsupported DB: " + dbType);
//        }
//    }
//}