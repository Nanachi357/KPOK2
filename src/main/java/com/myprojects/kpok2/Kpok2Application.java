package com.myprojects.kpok2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.myprojects.kpok2",
    "com.myprojects.kpok2.config",
    "com.myprojects.kpok2.runner",
    "com.myprojects.kpok2.service.navigation"
})
public class Kpok2Application {

    public static void main(String[] args) {
        SpringApplication.run(Kpok2Application.class, args);
    }

}
