package com.tickethub.booking.client;

import org.springframework.context.annotation.Configuration;

/**
 * Event calls can use the local URL by default for the presentation build.
 * Set services.event.use-eureka=true to resolve Event through Eureka.
 */
@Configuration
public class DiscoveryEventClientConfig {}
