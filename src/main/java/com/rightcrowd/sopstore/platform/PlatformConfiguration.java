package com.rightcrowd.sopstore.platform;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Spring configuration that enables platform-related configuration properties. */
@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformConfiguration {}
