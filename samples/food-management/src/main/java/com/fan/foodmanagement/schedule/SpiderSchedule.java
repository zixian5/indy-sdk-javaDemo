package com.fan.foodmanagement.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SpiderSchedule {
    @Scheduled(fixedRate = 1000*10)
    public void work()
    {
        System.out.println("working..........");
    }
}
