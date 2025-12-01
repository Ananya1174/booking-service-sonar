package com.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonDto {
    @NotBlank
    private String name;

    @Positive
    private Integer age;

    @NotBlank
    private String gender;

    // optional — some usages need seatNumber / mealPreference
    private String seatNumber;
    private String mealPreference;
}