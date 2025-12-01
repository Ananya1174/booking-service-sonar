package com.bookingservice.controller;

import com.bookingservice.dto.BookingRequest;
import com.bookingservice.dto.BookingResponseDto;
import com.bookingservice.dto.PersonDto;
import com.bookingservice.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for BookingController (standalone MockMvc).
 */
class BookingControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController controller;

    private static final String USER_HEADER = "X-User-Email";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void bookTicket_returns201_andLocation() throws Exception {
        BookingRequest req = BookingRequest.builder()
                .flightId(2L)
                .userEmail("alice@example.com")
                .numSeats(1)
                .build();
        req.setPassengers(List.of(PersonDto.builder().name("Bob").age(20).gender("M").seatNumber("1A").mealPreference("VEG").build()));

        BookingResponseDto resp = BookingResponseDto.builder()
                .pnr("PNR1")
                .flightId(2L)
                .userEmail("alice@example.com")
                .numSeats(1)
                .createdAt(Instant.now())
                .build();

        when(bookingService.createBooking(any(BookingRequest.class), eq("alice@example.com"))).thenReturn(resp);

        mockMvc.perform(post("/api/flight/booking/2")
                .contentType(MediaType.APPLICATION_JSON)
                .header(USER_HEADER, "alice@example.com")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/flight/ticket/PNR1")))
                .andExpect(jsonPath("$.pnr").value("PNR1"));

        verify(bookingService, times(1)).createBooking(any(BookingRequest.class), eq("alice@example.com"));
    }

    @Test
    void getByPnr_returns200_andBody() throws Exception {
        BookingResponseDto dto = BookingResponseDto.builder().pnr("P1").flightId(3L).userEmail("u@x.com").createdAt(Instant.now()).build();
        when(bookingService.getByPnr("P1")).thenReturn(dto);

        mockMvc.perform(get("/api/flight/ticket/P1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pnr").value("P1"));

        verify(bookingService).getByPnr("P1");
    }

    @Test
    void history_returnsList() throws Exception {
        BookingResponseDto a = BookingResponseDto.builder().pnr("H1").userEmail("u@t.com").build();
        when(bookingService.getHistoryByEmail("u@t.com")).thenReturn(List.of(a));

        mockMvc.perform(get("/api/flight/booking/history/u@t.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pnr").value("H1"));

        verify(bookingService).getHistoryByEmail("u@t.com");
    }

    @Test
    void cancel_returns200_andMessage() throws Exception {
        BookingResponseDto dto = BookingResponseDto.builder().pnr("C1").status("CANCELLED").build();
        when(bookingService.cancelBooking("C1", "user@x.com")).thenReturn(dto);

        mockMvc.perform(delete("/api/flight/booking/cancel/C1")
                .header(USER_HEADER, "user@x.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Booking cancelled successfully"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService).cancelBooking("C1", "user@x.com");
    }
}