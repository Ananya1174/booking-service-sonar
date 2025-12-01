package com.bookingservice.client;

import feign.Logger;
import org.springframework.context.annotation.Bean;

public class FlightClientConfig {
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}