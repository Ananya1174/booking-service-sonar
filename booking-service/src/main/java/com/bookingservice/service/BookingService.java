package com.bookingservice.service;

import com.bookingservice.client.FlightClient;
import com.bookingservice.client.dto.FlightDto;
import com.bookingservice.dto.BookingRequest;
import com.bookingservice.dto.BookingResponseDto;
import com.bookingservice.dto.PersonDto;
import com.bookingservice.model.Booking;
import com.bookingservice.model.Passenger;
import com.bookingservice.repository.BookingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final FlightClient flightClient;

    public BookingService(BookingRepository bookingRepository,
                          FlightClient flightClient) {
        this.bookingRepository = bookingRepository;
        this.flightClient = flightClient;
    }

    /**
     * Create a booking.
     * CircuitBreaker will redirect to createBookingFallback(...) on failures of flightClient.
     */
    @Transactional
    @CircuitBreaker(name = "flightClient", fallbackMethod = "createBookingFallback")
    public BookingResponseDto createBooking(BookingRequest request, String headerEmail) {
        log.debug("createBooking called: flightId={}, headerEmail={}, numSeats={}",
                request == null ? null : request.getFlightId(),
                headerEmail,
                request == null ? null : request.getNumSeats());

        // basic request validation
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (headerEmail == null || headerEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Email header is required");
        }

        // normalize userEmail: allow header to be canonical if body missing
        if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            request.setUserEmail(headerEmail);
        } else if (!headerEmail.equalsIgnoreCase(request.getUserEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Header user email must match request userEmail");
        }

        // validate flightId
        if (request.getFlightId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flightId is required");
        }

        // validate numSeats
        if (request.getNumSeats() == null || request.getNumSeats() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numSeats must be provided and > 0");
        }

        // validate passengers
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "passengers list is required and cannot be empty");
        }
        if (request.getPassengers().size() != request.getNumSeats()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "number of passengers must match numSeats");
        }

        // call flight service (Feign). This may throw exceptions which will trigger circuit-breaker
        FlightDto flight;
        try {
            flight = flightClient.getFlightById(request.getFlightId());
        } catch (Exception ex) {
            log.warn("Error calling flight service for id {}: {}", request.getFlightId(), ex.toString());
            // rethrow to let circuit breaker handle it (fallback invoked)
            throw ex;
        }

        if (flight == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Flight not found: " + request.getFlightId());
        }

        // check seat availability
        long availableSeats = Optional.ofNullable(flight.getSeats()).orElse(Collections.emptyList())
                .stream()
                .filter(s -> s.getStatus() != null && "AVAILABLE".equalsIgnoreCase(s.getStatus()))
                .count();

        if (availableSeats < request.getNumSeats()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough seats available: requested=" + request.getNumSeats() + ", available=" + availableSeats);
        }

        // calculate total price
        double totalPrice = (flight.getPrice() == null ? 0.0 : flight.getPrice()) * request.getNumSeats();

        Booking booking = new Booking();
        booking.setPnr(generatePnr());
        booking.setFlightId(request.getFlightId());
        booking.setUserEmail(request.getUserEmail());
        booking.setNumSeats(request.getNumSeats());
        booking.setTotalPrice(totalPrice);
        booking.setStatus("ACTIVE");
        booking.setCreatedAt(Instant.now());

        // map passengers into entity objects
        List<Passenger> passengers = request.getPassengers().stream().map(pdto -> {
            Passenger p = new Passenger();
            p.setPassengerName(pdto.getName());
            p.setGender(pdto.getGender());
            p.setAge(pdto.getAge());
            p.setSeatNumber(pdto.getSeatNumber());
            p.setMealPreference(pdto.getMealPreference());
            p.setBooking(booking);
            return p;
        }).collect(Collectors.toList());

        booking.setPassengers(passengers);

        Booking saved;
        try {
            saved = bookingRepository.save(booking);
        } catch (Exception ex) {
            log.error("Failed to save booking to DB: {}", ex.toString(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save booking");
        }

        log.info("Booking saved: pnr={}, flightId={}, user={}", saved.getPnr(), saved.getFlightId(), saved.getUserEmail());

        return convertToDto(saved);
    }

    /**
     * Resilience4j fallback method. Signature must match original method's parameters
     * plus an additional Throwable at the end.
     * We throw a Service Unavailable so controllers return 503.
     */
    public BookingResponseDto createBookingFallback(BookingRequest request, String headerEmail, Throwable t) {
        log.warn("createBookingFallback called for flightId={} user={} : {}",
                request == null ? null : request.getFlightId(),
                headerEmail, t == null ? "null" : t.toString());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Flight service unavailable. Try again later.");
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getByPnr(String pnr) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PNR not found"));
        return convertToDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponseDto> getHistoryByEmail(String email) {
        List<Booking> list = bookingRepository.findByUserEmailOrderByCreatedAtDesc(email);
        return list.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional
    public BookingResponseDto cancelBooking(String pnr, String headerEmail) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PNR not found"));

        if (!booking.getUserEmail().equalsIgnoreCase(headerEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the booking owner can cancel this booking");
        }

        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            return convertToDto(booking);
        }

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(Instant.now());
        Booking saved = bookingRepository.save(booking);

        log.info("Booking cancelled: pnr={}, flightId={}, user={}", saved.getPnr(), saved.getFlightId(), saved.getUserEmail());

        return convertToDto(saved);
    }

    private BookingResponseDto convertToDto(Booking b) {
        BookingResponseDto dto = new BookingResponseDto();
        dto.setPnr(b.getPnr());
        dto.setFlightId(b.getFlightId());
        dto.setUserEmail(b.getUserEmail());
        dto.setNumSeats(b.getNumSeats());
        dto.setTotalPrice(b.getTotalPrice());
        dto.setStatus(b.getStatus());
        dto.setCreatedAt(b.getCreatedAt());

        // map to PersonDto (used inside BookingResponseDto in your code)
        List<PersonDto> pinfos = Optional.ofNullable(b.getPassengers()).orElse(Collections.emptyList())
                .stream().map(p -> PersonDto.builder()
                        .name(p.getPassengerName())
                        .gender(p.getGender())
                        .age(p.getAge())
                        .seatNumber(p.getSeatNumber())
                        .mealPreference(p.getMealPreference())
                        .build())
                .collect(Collectors.toList());

        dto.setPassengers(pinfos);
        return dto;
    }

    private String generatePnr() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
    }
}