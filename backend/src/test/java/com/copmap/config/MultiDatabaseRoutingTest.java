package com.copmap.config;

import com.copmap.auth.entities.User;
import com.copmap.auth.repositories.UserRepository;
import com.copmap.officer.entity.OfficerProfile;
import com.copmap.officer.repository.OfficerProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("Multi-Database Routing & Architecture Isolation Test Suite")
public class MultiDatabaseRoutingTest {

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    @Qualifier("mysqlEntityManagerFactory")
    private EntityManagerFactory mysqlEntityManagerFactory;

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory postgresEntityManagerFactory;

    @Autowired
    @Qualifier("mysqlTransactionManager")
    private PlatformTransactionManager mysqlTransactionManager;

    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfficerProfileRepository officerRepository;

    @Nested
    @DisplayName("Architecture Validation: Separate vs Dynamic Routing")
    class RoutingArchitectureTests {

        @Test
        @DisplayName("Verify Separate Routing Architecture: Distinct DataSources, EntityManagers & TransactionManagers")
        void shouldVerifySeparateRoutingArchitecture() {
            // Requirement Check: Multi-Database Routing uses Separate Physical Routing
            assertThat(mysqlDataSource)
                    .as("MySQL and PostgreSQL DataSources must be separate physical instances")
                    .isNotNull()
                    .isNotSameAs(postgresDataSource);

            assertThat(mysqlEntityManagerFactory)
                    .as("MySQL and PostgreSQL EntityManagerFactories must be independent")
                    .isNotNull()
                    .isNotSameAs(postgresEntityManagerFactory);

            assertThat(mysqlTransactionManager)
                    .as("Transaction managers must be separate instances")
                    .isNotNull()
                    .isNotSameAs(postgresTransactionManager);

            assertThat(mysqlTransactionManager).isInstanceOf(JpaTransactionManager.class);
            assertThat(postgresTransactionManager).isInstanceOf(JpaTransactionManager.class);

            // Verify they are bound to their respective EntityManagerFactories
            JpaTransactionManager mysqlTm = (JpaTransactionManager) mysqlTransactionManager;
            JpaTransactionManager pgTm = (JpaTransactionManager) postgresTransactionManager;

            assertThat(mysqlTm.getEntityManagerFactory()).isSameAs(mysqlEntityManagerFactory);
            assertThat(pgTm.getEntityManagerFactory()).isSameAs(postgresEntityManagerFactory);
        }

        @Test
        @DisplayName("Verify Entity Boundary: MySQL manages IAM User, PostgreSQL manages OfficerProfile")
        void shouldVerifyPersistenceUnitEntityBoundaries() {
            EntityManager mysqlEm = mysqlEntityManagerFactory.createEntityManager();
            EntityManager pgEm = postgresEntityManagerFactory.createEntityManager();

            try {
                // MySQL Metamodel should contain User and not OfficerProfile
                assertThat(mysqlEm.getMetamodel().getEntities().stream()
                        .anyMatch(e -> e.getJavaType().equals(User.class)))
                        .as("MySQL Metamodel must contain User.class")
                        .isTrue();

                assertThat(mysqlEm.getMetamodel().getEntities().stream()
                        .noneMatch(e -> e.getJavaType().equals(OfficerProfile.class)))
                        .as("MySQL Metamodel must NOT contain tactical OfficerProfile.class")
                        .isTrue();

                // PostgreSQL Metamodel should contain OfficerProfile and not User
                assertThat(pgEm.getMetamodel().getEntities().stream()
                        .anyMatch(e -> e.getJavaType().equals(OfficerProfile.class)))
                        .as("PostgreSQL Metamodel must contain OfficerProfile.class")
                        .isTrue();

                assertThat(pgEm.getMetamodel().getEntities().stream()
                        .noneMatch(e -> e.getJavaType().equals(User.class)))
                        .as("PostgreSQL Metamodel must NOT contain User.class")
                        .isTrue();
            } finally {
                mysqlEm.close();
                pgEm.close();
            }
        }

        @Test
        @DisplayName("Verify Cross-Database Entity Rejection at Runtime")
        void shouldRejectCrossDatabaseEntityPersistence() {
            EntityManager mysqlEm = mysqlEntityManagerFactory.createEntityManager();
            EntityManager pgEm = postgresEntityManagerFactory.createEntityManager();

            try {
                OfficerProfile rogueOfficer = new OfficerProfile();
                rogueOfficer.setOfficerName("Rogue Officer");

                // Trying to persist an OfficerProfile in MySQL EntityManager must fail
                assertThatThrownBy(() -> {
                    mysqlEm.getTransaction().begin();
                    mysqlEm.persist(rogueOfficer);
                    mysqlEm.getTransaction().commit();
                }).as("Persisting tactical entity in MySQL EntityManager must throw IllegalArgumentException")
                  .isInstanceOf(IllegalArgumentException.class);

                User rogueUser = new User();
                rogueUser.setEmail("rogue@copmap.gov");

                // Trying to persist a User in PostgreSQL EntityManager must fail
                assertThatThrownBy(() -> {
                    pgEm.getTransaction().begin();
                    pgEm.persist(rogueUser);
                    pgEm.getTransaction().commit();
                }).as("Persisting auth entity in PostgreSQL EntityManager must throw IllegalArgumentException")
                  .isInstanceOf(IllegalArgumentException.class);
            } finally {
                if (mysqlEm.getTransaction().isActive()) mysqlEm.getTransaction().rollback();
                if (pgEm.getTransaction().isActive()) pgEm.getTransaction().rollback();
                mysqlEm.close();
                pgEm.close();
            }
        }
    }
}
