package org.mardon.database;

import jakarta.persistence.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cache;

@Entity
@Table(name = "tickets")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Ticket {
    @Id
    private Long id; // primary key (childId)

    private String seat;

    @ManyToOne
    @JoinColumn(name = "event_id") // Foreign key (FK) in the DB table
    private Event event;
}