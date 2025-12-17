package com.storedprocedure.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BatchInsertExecutorResolver {

    private final List<BatchInsertExecutor> executors;

    public BatchInsertExecutorResolver(List<BatchInsertExecutor> executors) {
        this.executors = executors;
    }

    public BatchInsertExecutor resolve(String dbType) {

        return executors.stream()
                .filter(e -> e.dbType().equalsIgnoreCase(dbType))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unsupported DB: " + dbType)
                );
    }
}
