package com.tasklean.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point. Boots the Spring context; {@code @EnableScheduling} activates
 * scheduled jobs such as the refresh-token cleanup.
 */
@SpringBootApplication
@EnableScheduling
public class TaskleanApiApplication {

	/**
	 * Starts the TasKlean API.
	 *
	 * @param args command-line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(TaskleanApiApplication.class, args);
	}

}
