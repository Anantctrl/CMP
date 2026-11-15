package com.copmap.config.database;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.copmap.auth.repositories",
    entityManagerFactoryRef = "mysqlEntityManagerFactory",
    transactionManagerRef = "mysqlTransactionManager"
)
public class MySqlDatabaseConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {

        return builder
                .dataSource(mysqlDataSource())
                .packages("com.copmap.auth.entities")
                .persistenceUnit("mysql")
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager mysqlTransactionManager(
            EntityManagerFactory mysqlEntityManagerFactory) {

        return new JpaTransactionManager(mysqlEntityManagerFactory);
    }
}
