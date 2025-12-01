package com.bookingservice.client;

import com.bookingservice.client.dto.FlightDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "flight-service", configuration = FlightClientConfig.class)
public interface FlightClient {

    @GetMapping("/api/flights/{id}")
    FlightDto getFlightById(@PathVariable("id") Long id);
}