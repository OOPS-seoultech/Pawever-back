package com.pawever.backend.global.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FlywayConfigurationTest {

    @Test
    void springBootFlywayAutoConfigurationIsOnTheRuntimeClasspath() {
        assertDoesNotThrow(() ->
                Class.forName("org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
        );
    }
}
