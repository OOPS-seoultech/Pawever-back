package com.pawever.backend.goodssurvey.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class GoodsSurveyClockConfig {

    @Bean
    public Clock goodsSurveyClock() {
        return Clock.systemUTC();
    }
}
