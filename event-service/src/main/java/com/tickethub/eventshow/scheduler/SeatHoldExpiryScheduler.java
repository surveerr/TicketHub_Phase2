package com.tickethub.eventshow.scheduler;

import com.tickethub.eventshow.repository.ShowSeatRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Component
public class SeatHoldExpiryScheduler {

    private final ShowSeatRepository repository;

    public SeatHoldExpiryScheduler(ShowSeatRepository repository){
        this.repository=repository;

    }

    @Scheduled(fixedDelayString = "${tickethub.seat-hold-expiry-ms:30000}")
    @Transactional
    public void releaseExpiredHolds(){ repository.releaseExpired(Instant.now());
    }
}
