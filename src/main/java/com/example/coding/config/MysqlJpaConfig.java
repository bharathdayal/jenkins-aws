package com.example.coding.config;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

//Configuration
//@EnableTransactionManagement
//@EnableJpaRepositories(entityManagerFactoryRef = "mysqlEmf",transactionManagerRef = "mysqlTxManager")
public class MysqlJpaConfig {

    /*@Bean
    @ConfigurationProperties("app.mysql.hikari")
    public DataSource mysqlDataSource(
            @Value("${app.mysql.url}") String url,
            @Value("${app.mysql.username}") String user,
            @Value("${app.mysql.password}") String pass) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url); ds.setUsername(user); ds.setPassword(pass);
        return ds;
    } */


}
