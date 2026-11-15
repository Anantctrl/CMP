package com.copmap.config.database;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.copmap.officer.repository",
        "com.copmap.operations.common.repository",
        "com.copmap.team.repository",
        "com.copmap.tracking.repository"
    },
    entityManagerFactoryRef = "postgresEntityManagerFactory",
    transactionManagerRef = "postgresTransactionManager"
)
public class PostgresDatabaseConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.datasource.postgres")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {

        return builder
                .dataSource(postgresDataSource())
                .packages(
                        "com.copmap.officer.entity",
                        "com.copmap.operations.common.entity",
                        "com.copmap.team.entity",
                        "com.copmap.tracking.entity"
                )
                .persistenceUnit("postgres")
                .properties(Map.of(
                        "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"
                ))
                .build();
    }

    @Bean
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory")
            EntityManagerFactory postgresEntityManagerFactory) {

        return new JpaTransactionManager(postgresEntityManagerFactory);
    }
}
