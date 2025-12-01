package com.bookingservice.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "passengers")
@Getter @Setter @NoArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="passenger_name", nullable=false)
    private String passengerName;

    private String gender;
    private Integer age;
    private String seatNumber;
    private String mealPreference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;
}