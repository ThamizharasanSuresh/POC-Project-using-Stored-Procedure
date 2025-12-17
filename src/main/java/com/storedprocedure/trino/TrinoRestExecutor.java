package com.storedprocedure.trino;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storedprocedure.dto.TrinoResponseBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
public class TrinoRestExecutor {

    @Value("${trino.api-url}")
    private String trinoApi;

    @Value("${spring.datasource.trino.username}")
    private String trinoUser;

    @Value("${spring.datasource.trino.catalog}")
    private String trinoCatalog;


    private final ObjectMapper mapper;

    public TrinoRestExecutor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.add("X-Trino-User", trinoUser);
        h.add("X-Trino-Catalog", trinoCatalog);
        h.setContentType(MediaType.TEXT_PLAIN);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    public TrinoResponseBean executeInitial(String query) throws Exception {
        RestTemplate rest = new RestTemplate();

        ResponseEntity<String> response = rest.exchange(
                trinoApi, HttpMethod.POST,
                new HttpEntity<>(query, headers()),
                String.class
        );

        return mapper.readValue(response.getBody(), new TypeReference<TrinoResponseBean>() {});
    }

    public TrinoResponseBean getNextPage(String nextUri) throws Exception {

        if (nextUri == null) return null;

        RestTemplate rest = new RestTemplate();

        ResponseEntity<String> response = rest.exchange(
                nextUri, HttpMethod.GET,
                new HttpEntity<>(headers()),
                String.class
        );

        return mapper.readValue(response.getBody(), new TypeReference<TrinoResponseBean>() {});
    }
}
