package com.example.respaldos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RespaldosApplication {

    public static void main(String[] args) {
        SpringApplication.run(RespaldosApplication.class, args);
    }

}
