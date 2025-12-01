package com.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequest {

    private String userEmail; // controller will fallback to header if missing

    @NotNull
    private Long flightId;

    @Positive
    private Integer numSeats;

    @NotEmpty
    @Valid
    private List<PersonDto> passengers;
}