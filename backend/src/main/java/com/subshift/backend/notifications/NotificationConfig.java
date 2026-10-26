package com.subshift.backend.notifications;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on @Async so alert emails are sent in the background. */
@Configuration
@EnableAsync
public class NotificationConfig {}
