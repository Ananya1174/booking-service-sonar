package com.bookingservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatDto {
    private String seatNumber;
    private String status; // AVAILABLE / BOOKED
}