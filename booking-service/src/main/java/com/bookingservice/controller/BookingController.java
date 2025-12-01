package com.bookingservice.controller;

import com.bookingservice.dto.BookingRequest;
import com.bookingservice.dto.BookingResponseDto;
import com.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flight")
public class BookingController {

    private final BookingService bookingService;
    private static final String USER_HEADER = "X-User-Email";

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /api/flight/booking/{flightId} - Create booking
     */
    @PostMapping("/booking/{flightId}")
    public ResponseEntity<BookingResponseDto> bookTicket(
            @PathVariable("flightId") Long flightId,
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(USER_HEADER) String userEmail) {

        // if body doesn't contain userEmail, set it from header
        if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            request.setUserEmail(userEmail);
        }

        if (!flightId.equals(request.getFlightId())) {
            return ResponseEntity.badRequest().build();
        }

        BookingResponseDto resp = bookingService.createBooking(request, userEmail);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/flight/ticket/{pnr}")
                .buildAndExpand(resp.getPnr())
                .toUri();

        return ResponseEntity.created(location).body(resp); // 201 Created
    }

    /**
     * GET /api/flight/ticket/{pnr}
     */
    @GetMapping("/ticket/{pnr}")
    public ResponseEntity<BookingResponseDto> getByPnr(@PathVariable("pnr") String pnr) {
        BookingResponseDto dto = bookingService.getByPnr(pnr);
        return ResponseEntity.ok(dto); // 200 OK
    }

    /**
     * GET /api/flight/booking/history/{emailId}
     */
    @GetMapping("/booking/history/{emailId}")
    public ResponseEntity<List<BookingResponseDto>> history(@PathVariable("emailId") String emailId) {
        List<BookingResponseDto> list = bookingService.getHistoryByEmail(emailId);
        return ResponseEntity.ok(list); // 200 OK
    }

    /**
     * DELETE /api/flight/booking/cancel/{pnr}
     * Requires header X-User-Email to match booking owner
     */
    @DeleteMapping("/booking/cancel/{pnr}")
    public ResponseEntity<Map<String,Object>> cancel(
            @PathVariable("pnr") String pnr,
            @RequestHeader(USER_HEADER) String userEmail) {

        BookingResponseDto dto = bookingService.cancelBooking(pnr, userEmail);

        Map<String,Object> resp = new HashMap<>();
        resp.put("message", "Booking cancelled successfully");
        resp.put("pnr", dto != null ? dto.getPnr() : pnr);
        resp.put("status", "CANCELLED");

        return ResponseEntity.ok(resp); // 200 OK with message
    }
}