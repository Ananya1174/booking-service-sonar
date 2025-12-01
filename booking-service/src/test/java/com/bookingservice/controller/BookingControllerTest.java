package com.bookingservice.controller;

import com.bookingservice.dto.BookingRequest;
import com.bookingservice.dto.BookingResponseDto;
import com.bookingservice.dto.PersonDto;
import com.bookingservice.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BookingControllerTest {

    private static final String BASE = "/api/flight";

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController controller;

    private MockMvc mockMvc;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private BookingRequest makeRequest(Long flightId, String userEmail) {
        BookingRequest r = BookingRequest.builder()
                .flightId(flightId)
                .userEmail(userEmail)
                .numSeats(2)
                .build();

        r.setPassengers(List.of(
                PersonDto.builder().name("Alice").age(28).gender("F").seatNumber("1A").mealPreference("VEG").build(),
                PersonDto.builder().name("Bob").age(30).gender("M").seatNumber("1B").mealPreference("VEG").build()
        ));
        return r;
    }

    private BookingResponseDto makeResponse(Long flightId, String userEmail, String pnr) {
        BookingResponseDto d = BookingResponseDto.builder()
                .pnr(pnr)
                .flightId(flightId)
                .userEmail(userEmail)
                .numSeats(2)
                .totalPrice(400.0)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
        d.setPassengers(List.of(
                PersonDto.builder().name("Alice").age(28).gender("F").seatNumber("1A").mealPreference("VEG").build(),
                PersonDto.builder().name("Bob").age(30).gender("M").seatNumber("1B").mealPreference("VEG").build()
        ));
        return d;
    }

    @Test
    void bookTicket_success_returns201_andLocationHeader() throws Exception {
        BookingRequest req = makeRequest(2L, "alice@example.com");
        BookingResponseDto resp = makeResponse(2L, "alice@example.com", "PNR12345");

        when(bookingService.createBooking(any(BookingRequest.class), eq("alice@example.com"))).thenReturn(resp);

        mockMvc.perform(post(BASE + "/booking/2")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Email", "alice@example.com")
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.pnr").value("PNR12345"))
                .andExpect(jsonPath("$.flightId").value(2))
                .andExpect(jsonPath("$.userEmail").value("alice@example.com"));

        verify(bookingService, times(1)).createBooking(any(BookingRequest.class), eq("alice@example.com"));
    }

    @Test
    void bookTicket_pathFlightMismatch_returns400_andServiceNotCalled() throws Exception {
        BookingRequest req = makeRequest(3L, "alice@example.com"); // flightId in body != path

        mockMvc.perform(post(BASE + "/booking/2")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Email", "alice@example.com")
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void getByPnr_found_returns200_andBody() throws Exception {
        BookingResponseDto resp = makeResponse(5L, "u@x.com", "PNR5");
        when(bookingService.getByPnr("PNR5")).thenReturn(resp);

        mockMvc.perform(get(BASE + "/ticket/PNR5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pnr").value("PNR5"))
                .andExpect(jsonPath("$.flightId").value(5));

        verify(bookingService, times(1)).getByPnr("PNR5");
    }

    @Test
    void getByPnr_missing_returns404() throws Exception {
        when(bookingService.getByPnr("MISSING")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "PNR not found"));

        mockMvc.perform(get(BASE + "/ticket/MISSING"))
                .andExpect(status().isNotFound());

        verify(bookingService, times(1)).getByPnr("MISSING");
    }

    @Test
    void history_returns200_listOfBookings() throws Exception {
        BookingResponseDto a = makeResponse(1L, "bob@x.com", "A1");
        BookingResponseDto b = makeResponse(2L, "bob@x.com", "B2");
        when(bookingService.getHistoryByEmail("bob@x.com")).thenReturn(List.of(a, b));

        mockMvc.perform(get(BASE + "/booking/history/bob@x.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pnr").exists())
                .andExpect(jsonPath("$[1].pnr").exists());

        verify(bookingService, times(1)).getHistoryByEmail("bob@x.com");
    }

    @Test
    void cancel_success_returns200_messageAndPnr() throws Exception {
        BookingResponseDto resp = makeResponse(7L, "owner@x.com", "CNL1");
        when(bookingService.cancelBooking("CNL1", "owner@x.com")).thenReturn(resp);

        mockMvc.perform(delete(BASE + "/booking/cancel/CNL1")
                .header("X-User-Email", "owner@x.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Booking cancelled successfully"))
                .andExpect(jsonPath("$.pnr").value("CNL1"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService, times(1)).cancelBooking("CNL1", "owner@x.com");
    }

    @Test
    void cancel_forbidden_whenNotOwner_returns403() throws Exception {
        when(bookingService.cancelBooking("CNL2", "other@x.com"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Only owner"));

        mockMvc.perform(delete(BASE + "/booking/cancel/CNL2")
                .header("X-User-Email", "other@x.com"))
                .andExpect(status().isForbidden());

        verify(bookingService, times(1)).cancelBooking("CNL2", "other@x.com");
    }
}