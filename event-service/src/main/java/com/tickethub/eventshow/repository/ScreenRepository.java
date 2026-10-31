package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.Screen;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
    Page<Screen> findByVenueId(Long venueId, Pageable pageable);

    boolean existsByVenueIdAndNameIgnoreCase(Long venueId, String name);
}
