package com.bookingservice.dto;

import com.bookingservice.client.dto.FlightDto;
import com.bookingservice.model.Booking;
import com.bookingservice.model.Passenger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Simple unit tests validating entity and DTO getters/setters / builders.
 */
class EntitiesAndDtoTest {

    @Test
    void booking_and_passenger_gettersSetters_work() {
        Booking b = new Booking();
        b.setPnr("T1");
        b.setUserEmail("u@x.com");
        b.setNumSeats(2);
        b.setCreatedAt(Instant.now());

        Passenger p = new Passenger();
        p.setPassengerName("John");
        p.setAge(30);
        p.setGender("M");
        p.setSeatNumber("1A");
        p.setMealPreference("VEG");
        p.setBooking(b);

        b.setPassengers(List.of(p));

        assertThat(b.getPnr()).isEqualTo("T1");
        assertThat(b.getPassengers()).hasSize(1);
        assertThat(b.getPassengers().get(0).getPassengerName()).isEqualTo("John");
    }

    @Test
    void seatDto_and_flightDto_builder_and_getters() {
        SeatDto s = SeatDto.builder().seatNumber("1A").status("AVAILABLE").build();
        assertThat(s.getSeatNumber()).isEqualTo("1A");
        assertThat(s.getStatus()).isEqualTo("AVAILABLE");

        FlightDto f = FlightDto.builder()
                .id(5L)
                .departureTime(LocalDateTime.now())
                .price(123.45)
                .build();

        f.setSeats(List.of(s));
        assertThat(f.getId()).isEqualTo(5L);
        assertThat(f.getSeats()).hasSize(1);
    }
}