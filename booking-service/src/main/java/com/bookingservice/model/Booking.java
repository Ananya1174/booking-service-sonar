package com.bookingservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pnr;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "num_seats", nullable = false)
    private Integer numSeats;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(nullable = false)
    private String status; // ACTIVE or CANCELLED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Passenger> passengers;
}