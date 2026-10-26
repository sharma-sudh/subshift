package com.subshift.backend.email;

import com.subshift.backend.config.OutlookOAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GeminiProperties.class, EmailProperties.class, OutlookOAuthProperties.class})
public class EmailConfig {}
