package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.Seat;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    Page<Seat> findByScreenId(Long screenId, Pageable pageable);

    boolean existsByScreenIdAndRowLabelIgnoreCaseAndSeatNumber(Long screenId, String rowLabel, Integer seatNumber);

    long countByScreenId(Long screenId);
}
