package com.tasklean.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskleanApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskleanApiApplication.class, args);
	}

}
