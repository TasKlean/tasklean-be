package com.tasklean.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * Application entry point. Boots the Spring context; {@code @EnableScheduling} activates
 * scheduled jobs such as the refresh-token cleanup. Also pins the JVM default time zone to UTC
 * before startup, so Hibernate-managed timestamps agree with the UTC {@code Clock} bean.
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
		// Hibernate's @CreationTimestamp/@UpdateTimestamp on a LocalDateTime read the JVM default
		// zone, not our UTC Clock bean. Without this, a host in a non-UTC zone writes those columns
		// in local time while service-set columns are UTC — two regimes in one table. Set before
		// SpringApplication.run so it applies to every timestamp the app can produce.
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
		SpringApplication.run(TaskleanApiApplication.class, args);
	}

}
