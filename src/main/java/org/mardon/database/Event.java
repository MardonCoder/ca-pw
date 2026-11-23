package org.mardon.database;

import jakarta.persistence.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Event {
    @Id
    private Long id;

    private String title;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Ticket> tickets = new ArrayList<>();

    protected Event() {}
    public Event(Long id, String title) { this.id = id; this.title = title; }
    public List<Ticket> getTickets() { return tickets; }
}
