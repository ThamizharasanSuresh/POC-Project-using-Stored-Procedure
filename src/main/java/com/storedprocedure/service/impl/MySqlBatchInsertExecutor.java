package com.storedprocedure.service.impl;

import com.storedprocedure.service.BatchInsertExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MySqlBatchInsertExecutor implements BatchInsertExecutor {


    @Override
    public void insertBatch(
            JdbcTemplate jdbc,
            String table,
            String columns,
            Object valuesBlock
    ) {

        System.out.println("Executing Stored Procedure for DB: mysql");

        jdbc.update(
                "CALL insert_csv_batch_mysql(?, ?, ?)",
                table, columns, valuesBlock
        );
    }

    @Override
    public String dbType() {
        return "mysql";
    }
}
