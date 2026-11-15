package com.centram.classzy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs (the periodic system-health check). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
