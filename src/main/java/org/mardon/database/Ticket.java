package org.mardon.database;

import jakarta.persistence.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "tickets")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Ticket {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    private String seatCode;

    protected Ticket() {}
    public Ticket(Long id, Event event, String seatCode) {
        this.id = id;
        this.event = event;
        this.seatCode = seatCode;
    }
}
