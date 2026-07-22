package com.jobhuntai.jobhunt_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class JobHuntBackendApplication {

	/**
	 * The PostgreSQL JDBC driver sends the JVM's default timezone in its connection
	 * startup packet. Windows JVMs resolve India to the legacy alias "Asia/Calcutta",
	 * which PostgreSQL 16's tzdata no longer accepts, so the connection is refused with
	 * {@code FATAL: invalid value for parameter "TimeZone"} before Liquibase can run.
	 * <p>
	 * This has to happen before {@code SpringApplication.run} — the DataSource connects
	 * during context refresh, so no application.yaml property is applied early enough.
	 * An explicit {@code -Duser.timezone} still wins.
	 */
	static void applyDefaultTimeZone() {
		if (System.getProperty("user.timezone") == null || System.getProperty("user.timezone").isBlank()) {
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		}
	}

	public static void main(String[] args) {
		applyDefaultTimeZone();
		SpringApplication.run(JobHuntBackendApplication.class, args);
	}

}
