package com.storedprocedure.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TrinoResponseBean {
    private String id;
    private String nextUri;
    private List<List<Object>> data;
    private List<Map<String, Object>> columns;
    private StatsBean stats;
}
