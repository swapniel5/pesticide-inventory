package com.agroshop.demodata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DemoDataDatabaseGuardTest {

    @Test
    void refusesRemotePostgresHostsEvenWhenDemoProfileIsEnabled() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://prod.example.com:5432/agroshop_demo");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        environment.setProperty("agroshop.demo-data.enabled", "true");

        DemoDataDatabaseGuard guard = new DemoDataDatabaseGuard(dataSource, environment);

        assertThatThrownBy(guard::verifySafeTarget)
                .isInstanceOf(DemoDataSafetyException.class)
                .hasMessageContaining("must be local database 'agroshop_demo'");
    }

    @Test
    void refusesTheNormalBusinessDatabase() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:5432/pesticide_inventory");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        environment.setProperty("agroshop.demo-data.enabled", "true");

        DemoDataDatabaseGuard guard = new DemoDataDatabaseGuard(dataSource, environment);

        assertThatThrownBy(guard::verifySafeTarget)
                .isInstanceOf(DemoDataSafetyException.class)
                .hasMessageContaining("must be local database 'agroshop_demo'");
    }
}
