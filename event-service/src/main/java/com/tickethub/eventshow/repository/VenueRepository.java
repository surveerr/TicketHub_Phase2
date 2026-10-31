package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.Venue;
import com.tickethub.eventshow.enums.ResourceStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    @Query("select v from Venue v where (:locationId is null or v.location.id=:locationId)" +
            " and (:status is null or v.status=:status) and " +
            "(:name is null or lower(v.name) like lower(concat('%',:name,'%')))")
    Page<Venue> search(@Param("locationId") Long locationId,
                       @Param("status") ResourceStatus status,
                       @Param("name") String name, Pageable pageable);

}
