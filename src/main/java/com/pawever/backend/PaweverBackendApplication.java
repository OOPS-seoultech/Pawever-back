package com.pawever.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class PaweverBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaweverBackendApplication.class, args);
	}

}
