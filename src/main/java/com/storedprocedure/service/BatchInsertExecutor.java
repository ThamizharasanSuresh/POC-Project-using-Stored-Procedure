package com.storedprocedure.service;

import org.springframework.jdbc.core.JdbcTemplate;

public interface BatchInsertExecutor {

    void insertBatch(
            JdbcTemplate jdbc,
            String table,
            String columns,
            Object valuesBlock
    );

    String dbType();
}

