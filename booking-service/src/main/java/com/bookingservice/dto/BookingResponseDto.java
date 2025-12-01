package com.bookingservice.dto;

import java.time.Instant;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponseDto {
    private String pnr;
    private Long flightId;
    private String userEmail;
    private Integer numSeats;
    private Double totalPrice;
    private String status;
    private Instant createdAt;
    private List<PersonDto> passengers;
}