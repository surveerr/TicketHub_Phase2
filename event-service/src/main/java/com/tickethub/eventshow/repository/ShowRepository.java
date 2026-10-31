package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.Show;
import com.tickethub.eventshow.enums.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("select s from Show s where (:eventId is null or s.event.id=:eventId) and" +
            " (:venueId is null or s.venue.id=:venueId) and" +
            " (:city is null or lower(s.venue.location.city)=lower(:city)) and" +
            " (:language is null or s.language=:language) and" +
            " (:format is null or s.showFormat=:format) and " +
            "(:status is null or s.status=:status) and " +
            "(:date is null or function('date',s.startTime)=:date)")
    Page<Show> search(@Param("eventId") Long eventId, @Param("venueId") Long venueId,
                      @Param("city") String city, @Param("date") LocalDate date,
                      @Param("language") Language language, @Param("format") ShowFormat format,
                      @Param("status") ShowStatus status, Pageable pageable);

    boolean existsByScreenIdAndStartTimeLessThanAndEndTimeGreaterThan(Long screenId, Instant endTime, Instant startTime);

    List<Show> findByEventId(Long eventId);

    long countByEventId(Long eventId);
}
