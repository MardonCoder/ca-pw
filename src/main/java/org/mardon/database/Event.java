package org.mardon.database;

import jakarta.persistence.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cache;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
// Turning on L2-caching for the entity Event
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Event {
    @Id
    private Long id; // primary key (parentId)

    // OneToMany relation: One Event is associated with multiple Tickets
    @OneToMany(mappedBy = "event")
    // Enable a separate collection cache (Collection Region) to store the list of ticket IDs
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Ticket> tickets = new ArrayList<>();
}
