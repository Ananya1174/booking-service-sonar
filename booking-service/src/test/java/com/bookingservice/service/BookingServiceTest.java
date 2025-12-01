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
 * Unit tests for BookingService.
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
    void setUp() {
        // MockitoExtension handles init
    }

    private FlightDto sampleFlightWithAvailableSeats(int availableSeats, double price) {
        FlightDto f = FlightDto.builder()
                .id(1L)
                .flightNumber("AI101")
                .airlineName("AirIndia")
                .origin("HYD")
                .destination("BLR")
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .price(price)
                .tripType("ONEWAY")
                .totalSeats(10)
                .build();

        List<com.bookingservice.dto.SeatDto> seats = new java.util.ArrayList<>();
        for (int i = 0; i < availableSeats; i++) {
            seats.add(new com.bookingservice.dto.SeatDto("S" + i, "AVAILABLE"));
        }
        seats.add(new com.bookingservice.dto.SeatDto("Sx", "BOOKED"));

        f.setSeats(seats);
        return f;
    }

    private BookingRequest buildBookingRequest(Long flightId, String userEmail, int numSeats) {
        BookingRequest r = BookingRequest.builder()
                .flightId(flightId)
                .userEmail(userEmail)
                .numSeats(numSeats)
                .build();

        List<PersonDto> passengers = new java.util.ArrayList<>();
        for (int i = 0; i < numSeats; i++) {
            passengers.add(PersonDto.builder()
                    .name("P" + i)
                    .age(25 + i)
                    .gender("M")
                    .seatNumber("S" + i)
                    .mealPreference("VEG")
                    .build());
        }
        r.setPassengers(passengers);
        return r;
    }

    @Test
    void createBooking_success_shouldReturnBookingResponse() {
        BookingRequest req = buildBookingRequest(10L, "alice@example.com", 2);

        FlightDto flight = sampleFlightWithAvailableSeats(5, 200.0);
        when(flightClient.getFlightById(10L)).thenReturn(flight);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setPnr("PNR00001");
            b.setCreatedAt(Instant.now());
            return b;
        });

        var resp = bookingService.createBooking(req, "alice@example.com");

        assertThat(resp).isNotNull();
        assertThat(resp.getPnr()).isNotNull();
        assertThat(resp.getFlightId()).isEqualTo(10L);
        assertThat(resp.getUserEmail()).isEqualTo("alice@example.com");
        assertThat(resp.getNumSeats()).isEqualTo(2);
        assertThat(resp.getTotalPrice()).isEqualTo(200.0 * 2);
        assertThat(resp.getPassengers()).hasSize(2);
        verify(flightClient, times(1)).getFlightById(10L);
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    void createBooking_headerMismatch_shouldThrowBadRequest() {
        BookingRequest req = buildBookingRequest(1L, "body@example.com", 1);

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.createBooking(req, "header@example.com"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_nullRequest_shouldThrowBadRequest() {
        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.createBooking(null, "x@y.com"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(flightClient, bookingRepository);
    }

    @Test
    void createBooking_flightNotFound_shouldThrowNotFound() {
        BookingRequest req = buildBookingRequest(99L, "alice@example.com", 1);
        when(flightClient.getFlightById(99L)).thenReturn(null);

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.createBooking(req, "alice@example.com"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_notEnoughSeats_shouldThrowConflict() {
        BookingRequest req = buildBookingRequest(2L, "alice@example.com", 4);
        FlightDto flight = sampleFlightWithAvailableSeats(2, 100.0);
        when(flightClient.getFlightById(2L)).thenReturn(flight);

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.createBooking(req, "alice@example.com"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_passengerCountMismatch_shouldThrowBadRequest() {
        BookingRequest req = BookingRequest.builder()
                .flightId(3L)
                .userEmail("a@b.com")
                .numSeats(2)
                .build();
        req.setPassengers(List.of(PersonDto.builder().name("only").age(20).gender("F").build()));

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.createBooking(req, "a@b.com"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(flightClient, bookingRepository);
    }

    @Test
    void getByPnr_found_returnsDto() {
        Booking booking = new Booking();
        booking.setPnr("PNRABC");
        booking.setFlightId(5L);
        booking.setUserEmail("u@x.com");
        booking.setNumSeats(1);
        booking.setTotalPrice(123.0);
        booking.setStatus("ACTIVE");
        booking.setCreatedAt(Instant.now());

        Passenger p = new Passenger();
        p.setPassengerName("John");
        p.setGender("M");
        p.setAge(30);
        p.setSeatNumber("1A");
        p.setMealPreference("VEG");
        p.setBooking(booking);
        booking.setPassengers(List.of(p));

        when(bookingRepository.findByPnr("PNRABC")).thenReturn(Optional.of(booking));

        var dto = bookingService.getByPnr("PNRABC");

        assertThat(dto).isNotNull();
        assertThat(dto.getPnr()).isEqualTo("PNRABC");
        assertThat(dto.getPassengers()).hasSize(1);
    }

    @Test
    void getByPnr_missing_throwsNotFound() {
        when(bookingRepository.findByPnr("MISSING")).thenReturn(Optional.empty());

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.getByPnr("MISSING"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelBooking_success_shouldReturnCancelled() {
        Booking booking = new Booking();
        booking.setPnr("CNL1");
        booking.setFlightId(7L);
        booking.setUserEmail("owner@x.com");
        booking.setNumSeats(1);
        booking.setStatus("ACTIVE");
        booking.setCreatedAt(Instant.now());

        when(bookingRepository.findByPnr("CNL1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setStatus("CANCELLED");
            b.setCancelledAt(Instant.now());
            return b;
        });

        var res = bookingService.cancelBooking("CNL1", "owner@x.com");

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo("CANCELLED");
        verify(bookingRepository, times(1)).save(any());
    }

    @Test
    void cancelBooking_forbidden_whenNotOwner() {
        Booking booking = new Booking();
        booking.setPnr("CNL2");
        booking.setUserEmail("owner@x.com");
        booking.setStatus("ACTIVE");

        when(bookingRepository.findByPnr("CNL2")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = catchThrowableOfType(
                () -> bookingService.cancelBooking("CNL2", "other@x.com"),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getHistoryByEmail_returnsList() {
        Booking b1 = new Booking();
        b1.setPnr("H1"); b1.setUserEmail("u@t.com"); b1.setCreatedAt(Instant.now());
        Booking b2 = new Booking();
        b2.setPnr("H2"); b2.setUserEmail("u@t.com"); b2.setCreatedAt(Instant.now());

        when(bookingRepository.findByUserEmailOrderByCreatedAtDesc("u@t.com"))
                .thenReturn(List.of(b1, b2));

        var list = bookingService.getHistoryByEmail("u@t.com");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getPnr()).isIn("H1", "H2");
    }
}