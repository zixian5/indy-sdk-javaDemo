package com.fan.foodmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodManagementApplication.class, args);
    }

}
