package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface LocationRepository extends JpaRepository<Location, Long> {

    boolean existsByCityIgnoreCaseAndStateIgnoreCaseAndCountryIgnoreCase(String city, String state, String country);
}
