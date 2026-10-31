package com.tickethub.eventshow.scheduler;

import com.tickethub.eventshow.entity.Show;
import com.tickethub.eventshow.enums.ShowStatus;
import com.tickethub.eventshow.repository.ShowRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Component
public class ShowStatusScheduler {

    private final ShowRepository repository;

    public ShowStatusScheduler(ShowRepository repository){
        this.repository=repository;
    }

    @Scheduled(fixedDelayString = "${tickethub.show-status-check-ms:30000}")
    @Transactional
    public void updateShowStatuses(){
        Instant now=Instant.now();
        for(Show show: repository.findAll()){
            if(show.getStatus()==ShowStatus.SCHEDULED && !show.getStartTime().isAfter(now.plusSeconds(7200))) show.setStatus(ShowStatus.BOOKING_OPEN);
            else if(show.getStatus()==ShowStatus.BOOKING_OPEN && !show.getStartTime().isAfter(now)) show.setStatus(ShowStatus.BOOKING_CLOSED);
            else if(show.getStatus()==ShowStatus.BOOKING_CLOSED && !show.getEndTime().isAfter(now)) show.setStatus(ShowStatus.COMPLETED);
        }
    }
}
