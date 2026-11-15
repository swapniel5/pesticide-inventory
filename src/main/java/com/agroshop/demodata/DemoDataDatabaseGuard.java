package com.agroshop.demodata;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class DemoDataDatabaseGuard {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private static final String DEMO_DATABASE = "agroshop_demo";

    private final DataSource dataSource;
    private final Environment environment;

    DemoDataDatabaseGuard(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    void verifySafeTarget() {
        if (!environment.acceptsProfiles(Profiles.of("demo"))) {
            throw new DemoDataSafetyException("Demo data operations require the 'demo' Spring profile");
        }
        if (!environment.getProperty("agroshop.demo-data.enabled", Boolean.class, false)) {
            throw new DemoDataSafetyException("Set agroshop.demo-data.enabled=true to allow demo data operations");
        }

        String databaseUrl = getDatabaseUrl();
        if (databaseUrl.startsWith("jdbc:postgresql:")) {
            verifyPostgresTarget(databaseUrl);
            return;
        }
        if (databaseUrl.startsWith("jdbc:h2:mem:agroshop_demo")) {
            return;
        }
        throw new DemoDataSafetyException(
                "Demo data is allowed only on local PostgreSQL database 'agroshop_demo' "
                        + "or an in-memory H2 database named 'agroshop_demo'");
    }

    private void verifyPostgresTarget(String databaseUrl) {
        URI uri;
        try {
            uri = URI.create(databaseUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new DemoDataSafetyException("Could not validate the demo database URL", exception);
        }
        String databaseName = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (!LOOPBACK_HOSTS.contains(uri.getHost()) || !DEMO_DATABASE.equals(databaseName)) {
            throw new DemoDataSafetyException(
                    "Refusing demo data operations: the target must be local database 'agroshop_demo'");
        }
    }

    private String getDatabaseUrl() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException exception) {
            throw new DemoDataSafetyException("Could not verify the demo database connection", exception);
        }
    }
}
