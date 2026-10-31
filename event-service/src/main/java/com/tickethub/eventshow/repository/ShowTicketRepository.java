package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.ShowTicket;
import com.tickethub.eventshow.enums.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ShowTicketRepository extends JpaRepository<ShowTicket, Long> {
    List<ShowTicket> findByShowId(Long showId);

    Optional<ShowTicket> findByShowIdAndTicketCategory(Long showId, TicketCategory category);
}
