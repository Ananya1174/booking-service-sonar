package com.bookingservice.service;

import com.bookingservice.client.FlightClient;
import com.bookingservice.client.dto.FlightDto;
import com.bookingservice.dto.BookingRequest;
import com.bookingservice.dto.PersonDto;
import com.bookingservice.model.Booking;
import com.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Additional BookingService unit tests to cover edge behavior.
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BookingServiceExtraTest {

    @Mock
    BookingRepository bookingRepository;

    @Mock
    FlightClient flightClient;

    @InjectMocks
    BookingService bookingService;

    private FlightDto sampleFlight;

    @BeforeEach
    void init() {
        sampleFlight = FlightDto.builder()
                .id(1L)
                .price(100.0)
                .totalSeats(5)
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();
        sampleFlight.setSeats(List.of(new com.bookingservice.dto.SeatDto("1A","AVAILABLE")));
    }

    @Test
    void createBooking_handlesNullPrice() {
        sampleFlight.setPrice(null);
        when(flightClient.getFlightById(1L)).thenReturn(sampleFlight);

        BookingRequest r = BookingRequest.builder().flightId(1L).userEmail("a@b.com").numSeats(1).build();
        r.setPassengers(List.of(PersonDto.builder().name("X").age(20).gender("M").build()));

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = bookingService.createBooking(r, "a@b.com");
        // price null => treated as 0.0 * seats
        assertThat(resp.getTotalPrice()).isEqualTo(0.0);
    }

    @Test
    void createBooking_mapsPassengers_evenIfSeatNumberMissing() {
        when(flightClient.getFlightById(1L)).thenReturn(sampleFlight);

        BookingRequest r = BookingRequest.builder().flightId(1L).userEmail("u@x.com").numSeats(1).build();
        // passenger without seatNumber and mealPreference (optional)
        r.setPassengers(List.of(PersonDto.builder().name("Y").age(25).gender("F").build()));

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        var dto = bookingService.createBooking(r, "u@x.com");
        assertThat(dto.getPassengers()).hasSize(1);
        assertThat(dto.getPassengers().get(0).getName()).isEqualTo("Y");
    }

    @Test
    void getHistoryByEmail_whenRepoEmpty_returnsEmptyList() {
        when(bookingRepository.findByUserEmailOrderByCreatedAtDesc("noone")).thenReturn(List.of());
        var list = bookingService.getHistoryByEmail("noone");
        assertThat(list).isEmpty();
    }
}