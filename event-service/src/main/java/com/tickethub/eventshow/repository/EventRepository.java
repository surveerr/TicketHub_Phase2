package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.Event;
import com.tickethub.eventshow.enums.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select distinct e from Event e left join e.languages l" +
            " where (:q is null or lower(e.title) like" +
            " lower(concat('%',:q,'%'))) and " +
            "(:language is null or l=:language) and " +
            "(:status is null or e.status=:status) and " +
            "(:type is null or e.eventType=:type) and " +
            "(:category is null or e.category=:category) and" +
            " (:city is null or exists (select 1 from Show s join s.venue v join v.location loc where" +
            " s.event=e and loc.city=:city and s.startTime >= CURRENT_TIMESTAMP and " +
            "s.status in (com.tickethub.eventshow.enums.ShowStatus.SCHEDULED, com.tickethub.eventshow.enums.ShowStatus.BOOKING_OPEN)))")
    Page<Event> search(@Param("q") String q, @Param("language") Language language, @Param("status") EventStatus status, @Param("type") EventType type, @Param("category") Category category, @Param("city") String city, Pageable pageable);
}
