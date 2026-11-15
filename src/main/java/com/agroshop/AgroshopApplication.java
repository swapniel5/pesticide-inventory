package com.agroshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgroshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgroshopApplication.class, args);
    }
}
