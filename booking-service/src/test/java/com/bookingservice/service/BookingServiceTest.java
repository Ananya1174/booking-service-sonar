package com.bookingservice.service;

import com.bookingservice.client.FlightClient;
import com.bookingservice.client.dto.FlightDto;
import com.bookingservice.dto.BookingRequest;
import com.bookingservice.dto.PersonDto;
import com.bookingservice.model.Booking;
import com.bookingservice.model.Passenger;
import com.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingService
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private FlightClient flightClient;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void init() {
        // MockitoExtension initializes mocks
    }

    // Helper: create FlightDto with a given number of AVAILABLE seats and price
    private FlightDto flightWithAvailableSeats(int avail, double price) {
        FlightDto f = FlightDto.builder()
                .id(10L)
                .flightNumber("AI101")
                .airlineName("Indigo")
                .origin("HYD")
                .destination("BLR")
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .price(price)
                .tripType("ONEWAY")
                .totalSeats(20)
                .build();

        List<com.bookingservice.dto.SeatDto> seats = new java.util.ArrayList<>();
        for (int i = 0; i < avail; i++) seats.add(new com.bookingservice.dto.SeatDto("S" + i, "AVAILABLE"));
        // add some booked seats
        seats.add(new com.bookingservice.dto.SeatDto("B1", "BOOKED"));
        f.setSeats(seats);
        return f;
    }

    private BookingRequest makeRequest(Long flightId, String user, int seats) {
        BookingRequest r = BookingRequest.builder()
                .flightId(flightId)
                .userEmail(user)
                .numSeats(seats)
                .build();

        List<PersonDto> list = new java.util.ArrayList<>();
        for (int i = 0; i < seats; i++) {
            list.add(PersonDto.builder()
                    .name("P" + i)
                    .age(20 + i)
                    .gender("M")
                    .seatNumber("S" + i)
                    .mealPreference("VEG")
                    .build());
        }
        r.setPassengers(list);
        return r;
    }

    @Test
    void createBooking_success_savesAndReturnsDto() {
        BookingRequest req = makeRequest(10L, "alice@example.com", 2);
        FlightDto flight = flightWithAvailableSeats(5, 150.0);
        when(flightClient.getFlightById(10L)).thenReturn(flight);

        // capture saved booking and simulate DB produced values
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setPnr("PNR12345");
            b.setCreatedAt(Instant.now());
            return b;
        });

        var resp = bookingService.createBooking(req, "alice@example.com");
        assertThat(resp).isNotNull();
        assertThat(resp.getPnr()).isEqualTo("PNR12345");
        assertThat(resp.getFlightId()).isEqualTo(10L);
        assertThat(resp.getNumSeats()).isEqualTo(2);
        assertThat(resp.getTotalPrice()).isEqualTo(150.0 * 2);
        assertThat(resp.getPassengers()).hasSize(2);

        verify(flightClient, times(1)).getFlightById(10L);
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    void createBooking_nullRequest_throwsBadRequest() {
        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.createBooking(null, "u@x.com"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(flightClient, bookingRepository);
    }

    @Test
    void createBooking_headerMismatch_throwsBadRequest() {
        BookingRequest req = makeRequest(1L, "body@example.com", 1);
        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.createBooking(req, "header@x.com"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(flightClient, bookingRepository);
    }

    @Test
    void createBooking_flightNotFound_throwsNotFound() {
        BookingRequest req = makeRequest(99L, "a@b.com", 1);
        when(flightClient.getFlightById(99L)).thenReturn(null);

        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.createBooking(req, "a@b.com"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_notEnoughSeats_throwsConflict() {
        BookingRequest req = makeRequest(2L, "a@b.com", 4);
        when(flightClient.getFlightById(2L)).thenReturn(flightWithAvailableSeats(2, 100.0));

        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.createBooking(req, "a@b.com"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_passengerCountMismatch_throwsBadRequest() {
        BookingRequest r = BookingRequest.builder()
                .flightId(3L)
                .userEmail("u@a.com")
                .numSeats(2)
                .build();
        r.setPassengers(List.of(PersonDto.builder().name("only").age(20).gender("F").build()));

        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.createBooking(r, "u@a.com"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(flightClient, bookingRepository);
    }

    @Test
    void createBooking_flightServiceThrows_triggersFallback_throwServiceUnavailable() {
        BookingRequest req = makeRequest(5L, "u@x.com", 1);
        when(flightClient.getFlightById(5L)).thenThrow(new RuntimeException("connection refused"));

        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.createBooking(req, "u@x.com"),
                ResponseStatusException.class);
        // fallback returns SERVICE_UNAVAILABLE
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getByPnr_found_returnsDto() {
        Booking booking = new Booking();
        booking.setPnr("P1");
        booking.setFlightId(7L);
        booking.setUserEmail("u@x.com");
        booking.setNumSeats(1);
        booking.setTotalPrice(100.0);
        booking.setStatus("ACTIVE");
        booking.setCreatedAt(Instant.now());
        Passenger p = new Passenger();
        p.setPassengerName("John");
        p.setAge(30);
        p.setGender("M");
        p.setSeatNumber("1A");
        p.setMealPreference("VEG");
        p.setBooking(booking);
        booking.setPassengers(List.of(p));

        when(bookingRepository.findByPnr("P1")).thenReturn(Optional.of(booking));

        var dto = bookingService.getByPnr("P1");
        assertThat(dto).isNotNull();
        assertThat(dto.getPnr()).isEqualTo("P1");
        assertThat(dto.getPassengers()).hasSize(1);
    }

    @Test
    void getByPnr_missing_throwsNotFound() {
        when(bookingRepository.findByPnr("NONE")).thenReturn(Optional.empty());
        ResponseStatusException ex = catchThrowableOfType(() -> bookingService.getByPnr("NONE"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelBooking_success_setsCancelled() {
        Booking booking = new Booking();
        booking.setPnr("C1");
        booking.setUserEmail("owner@x.com");
        booking.setStatus("ACTIVE");
        when(bookingRepository.findByPnr("C1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = bookingService.cancelBooking("C1", "owner@x.com");
        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo("CANCELLED");
        verify(bookingRepository).save(any());
    }

    @Test
    void cancelBooking_forbidden_whenNotOwner() {
        Booking booking = new Booking();
        booking.setPnr("C2");
        booking.setUserEmail("owner@x.com");
        booking.setStatus("ACTIVE");
        when(bookingRepository.findByPnr("C2")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.cancelBooking("C2", "other@x.com"),
                ResponseStatusException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getHistoryByEmail_returnsList() {
        Booking b1 = new Booking(); b1.setPnr("H1"); b1.setUserEmail("u@t.com");
        Booking b2 = new Booking(); b2.setPnr("H2"); b2.setUserEmail("u@t.com");
        when(bookingRepository.findByUserEmailOrderByCreatedAtDesc("u@t.com")).thenReturn(List.of(b1, b2));

        var list = bookingService.getHistoryByEmail("u@t.com");
        assertThat(list).hasSize(2);
    }
}