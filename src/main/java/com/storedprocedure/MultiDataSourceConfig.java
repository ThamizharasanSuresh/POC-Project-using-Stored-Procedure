package com.storedprocedure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import javax.sql.DataSource;


@Configuration
public class MultiDataSourceConfig {

    @Value("${app.target-db}")
    private String targetDb;


    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(
            @Value("${spring.datasource.postgres.url}") String url,
            @Value("${spring.datasource.postgres.username}") String username,
            @Value("${spring.datasource.postgres.password}") String password,
            @Value("${spring.datasource.postgres.driver-class-name}") String driver
    ) {
        return buildDataSource(url, username, password, driver);
    }

    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(
            @Value("${spring.datasource.mysql.url}") String url,
            @Value("${spring.datasource.mysql.username}") String username,
            @Value("${spring.datasource.mysql.password}") String password,
            @Value("${spring.datasource.mysql.driver-class-name}") String driver
    ) {
        return buildDataSource(url, username, password, driver);
    }

    @Bean(name = "oracleDataSource")
    public DataSource oracleDataSource(
            @Value("${spring.datasource.oracle.url}") String url,
            @Value("${spring.datasource.oracle.username}") String username,
            @Value("${spring.datasource.oracle.password}") String password,
            @Value("${spring.datasource.oracle.driver-class-name}") String driver
    ) {
        return buildDataSource(url, username, password, driver);
    }

    private DataSource buildDataSource(String url, String username, String password, String driver) {
        DataSourceProperties dsp = new DataSourceProperties();
        dsp.setUrl(url);
        dsp.setUsername(username);
        dsp.setPassword(password);
        dsp.setDriverClassName(driver);
        return dsp.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "dynamicDataSource")
    public DataSource dynamicDataSource(
            @Qualifier("postgresDataSource") DataSource postgres,
            @Qualifier("mysqlDataSource") DataSource mysql,
            @Qualifier("oracleDataSource") DataSource oracle
    ) {
        return switch (targetDb.toLowerCase()) {
            case "postgres" -> postgres;
            case "mysql" -> mysql;
            case "oracle" -> oracle;
            default -> throw new IllegalArgumentException("Unknown DB: " + targetDb);
        };
    }

    @Primary
    @Bean(name = "targetJdbcTemplate")
    public JdbcTemplate targetJdbcTemplate(@Qualifier("dynamicDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

}
