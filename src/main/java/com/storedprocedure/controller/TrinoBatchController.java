package com.storedprocedure.controller;

import com.storedprocedure.service.TrinoBatchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trino-batch")
public class TrinoBatchController {

    private final TrinoBatchService trinoBatchService;

    private TrinoBatchController(TrinoBatchService trinoBatchService) {
        this.trinoBatchService = trinoBatchService;
    }

    @PostMapping("/run")
    public String runBatch(@RequestParam(required = false) String query) throws Exception {
        return trinoBatchService.runBatchQuery(query);
    }
}
