package ru.abs7.b24support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class B24SupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(B24SupportApplication.class, args);
    }
}
