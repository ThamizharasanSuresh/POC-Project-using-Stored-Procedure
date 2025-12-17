package com.storedprocedure.service.impl;

import com.storedprocedure.service.BatchInsertExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


@Component
public class PostgresBatchInsertExecutor implements BatchInsertExecutor {

    @Override
    public void insertBatch(
            JdbcTemplate jdbc,
            String table,
            String columns,
            Object valuesBlock
    ) {

        System.out.println("Executing Stored Procedure for DB: postgres");

        jdbc.update(
                "CALL insert_csv_batch_pg(?, ?, ?)",
                table, columns, valuesBlock
        );
    }

    @Override
    public String dbType() {
        return "postgres";
    }
}
