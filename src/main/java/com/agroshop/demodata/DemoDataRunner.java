package com.agroshop.demodata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "agroshop.demo-data.run-on-startup", havingValue = "true")
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataRunner.class);

    private final DemoDataService demoDataService;
    private final Environment environment;

    public DemoDataRunner(DemoDataService demoDataService, Environment environment) {
        this.demoDataService = demoDataService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean resetOnly = environment.getProperty("agroshop.demo-data.reset-only", Boolean.class, false);
        DemoDataSummary summary = resetOnly ? demoDataService.reset() : demoDataService.seed();
        logger.info("{}", summary.report());
    }
}
