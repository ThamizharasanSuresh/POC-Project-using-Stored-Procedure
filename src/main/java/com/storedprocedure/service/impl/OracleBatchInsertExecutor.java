package com.storedprocedure.service.impl;

import com.storedprocedure.service.BatchInsertExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OracleBatchInsertExecutor implements BatchInsertExecutor {

    @Override
    public void insertBatch(
            JdbcTemplate jdbc,
            String table,
            String columns,
            Object valuesBlock
    ) {

        System.out.println("Executing Stored Procedure for DB: oracle");

        jdbc.update(
                "CALL STORED_PROCEDURE.insert_csv_batch_oracle(?, ?, ?)",
                table, columns, valuesBlock
        );
    }

    @Override
    public String dbType() {
        return "oracle";
    }
}
