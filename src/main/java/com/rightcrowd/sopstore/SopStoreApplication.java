package com.rightcrowd.sopstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point for the SOP Store Spring Boot application. */
@SpringBootApplication
@Modulithic(systemName = "sopstore")
@EnableScheduling
public class SopStoreApplication {

  /** Boots the SOP Store application. */
  public static void main(String[] args) {
    SpringApplication.run(SopStoreApplication.class, args);
  }
}
