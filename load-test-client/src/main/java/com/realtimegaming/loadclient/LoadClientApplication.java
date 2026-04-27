package com.realtimegaming.loadclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LoadConfig.class)
public class LoadClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadClientApplication.class, args);
    }
}
