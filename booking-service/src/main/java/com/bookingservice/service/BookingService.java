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

/**
 * BookingService - refactored to reduce cognitive complexity.
 *
 * Responsibilities split into:
 *  - validateAndNormalizeRequest
 *  - fetchFlightOrThrow
 *  - ensureSeatAvailabilityOrThrow
 *  - buildBookingEntity
 *  - persistBooking
 *  - convertToDto
 */
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

        // validate and normalize incoming request (may set userEmail from header)
        validateAndNormalizeRequest(request, headerEmail);

        // fetch flight (may throw and trigger circuit-breaker)
        FlightDto flight = fetchFlightOrThrow(request.getFlightId());

        // ensure seats available (no need to store result — method throws if not enough)
        ensureSeatAvailabilityOrThrow(flight, request.getNumSeats());

        // price calculation
        double totalPrice = calculateTotalPrice(flight.getPrice(), request.getNumSeats());

        // build entity and persist
        Booking booking = buildBookingEntity(request, totalPrice);
        Booking saved = persistBookingOrThrow(booking);

        log.info("Booking saved: pnr={}, flightId={}, user={}", 
                saved.getPnr(), saved.getFlightId(), saved.getUserEmail());

        return convertToDto(saved);
    }

    /* ---------- helpers (extracted to reduce cognitive complexity) ---------- */

    private void validateAndNormalizeRequest(BookingRequest request, String headerEmail) {
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

        if (request.getFlightId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flightId is required");
        }

        if (request.getNumSeats() == null || request.getNumSeats() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numSeats must be provided and > 0");
        }

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "passengers list is required and cannot be empty");
        }
        if (request.getPassengers().size() != request.getNumSeats()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "number of passengers must match numSeats");
        }
    }

    private FlightDto fetchFlightOrThrow(Long flightId) {
        try {
            FlightDto flight = flightClient.getFlightById(flightId);

            if (flight == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Flight not found: " + flightId
                );
            }

            return flight;

        } catch (ResponseStatusException rse) {
            log.warn("Flight service returned an error for flightId={}: {}", flightId, rse.getReason());
            throw rse; // keeps original status
        } catch (Exception ex) {
            log.error("Unexpected error calling flight service for flightId={}", flightId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Error contacting flight service for flightId=" + flightId,
                    ex
            );
        }
    }

    private long ensureSeatAvailabilityOrThrow(FlightDto flight, Integer requestedSeats) {
        long availableSeats = Optional.ofNullable(flight.getSeats()).orElse(Collections.emptyList())
                .stream()
                .filter(s -> s.getStatus() != null && "AVAILABLE".equalsIgnoreCase(s.getStatus()))
                .count();

        if (availableSeats < requestedSeats) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough seats available: requested=" + requestedSeats + ", available=" + availableSeats);
        }
        return availableSeats;
    }

    private double calculateTotalPrice(Double pricePerSeat, Integer numSeats) {
        double price = (pricePerSeat == null) ? 0.0 : pricePerSeat;
        return price * numSeats;
    }

    private Booking buildBookingEntity(BookingRequest request, double totalPrice) {
        Booking booking = new Booking();
        booking.setPnr(generatePnr());
        booking.setFlightId(request.getFlightId());
        booking.setUserEmail(request.getUserEmail());
        booking.setNumSeats(request.getNumSeats());
        booking.setTotalPrice(totalPrice);
        booking.setStatus("ACTIVE");
        booking.setCreatedAt(Instant.now());

        List<Passenger> passengers = request.getPassengers().stream().map(pdto -> {
            Passenger p = new Passenger();
            p.setPassengerName(pdto.getName());
            p.setGender(pdto.getGender());
            p.setAge(pdto.getAge());
            p.setSeatNumber(pdto.getSeatNumber());
            p.setMealPreference(pdto.getMealPreference());
            p.setBooking(booking);
            return p;
        }).toList();

        booking.setPassengers(passengers);
        return booking;
    }

    private Booking persistBookingOrThrow(Booking booking) {
        try {
            return bookingRepository.save(booking);
        } catch (Exception ex) {
            log.error("Failed to save booking to DB: {}", ex.toString(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save booking");
        }
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

    /* ---------- existing methods unchanged ---------- */

    @Transactional(readOnly = true)
    public BookingResponseDto getByPnr(String pnr) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PNR not found"));
        return convertToDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponseDto> getHistoryByEmail(String email) {
        List<Booking> list = bookingRepository.findByUserEmailOrderByCreatedAtDesc(email);
        return list.stream().map(this::convertToDto).toList();
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

        List<PersonDto> pinfos = Optional.ofNullable(b.getPassengers()).orElse(Collections.emptyList())
                .stream().map(p -> PersonDto.builder()
                        .name(p.getPassengerName())
                        .gender(p.getGender())
                        .age(p.getAge())
                        .seatNumber(p.getSeatNumber())
                        .mealPreference(p.getMealPreference())
                        .build())
                .toList();

        dto.setPassengers(pinfos);
        return dto;
    }

    private String generatePnr() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")   // ✔ faster, no regex
                .substring(0, 8)
                .toUpperCase();
    }
}