package com.cloudlenshq.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CloudlenshqApplication {
  public static void main(String[] args) {
    try {
      io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
          .ignoreIfMissing()
          .load();
      dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    } catch (Exception e) {
    }
    
    SpringApplication.run(CloudlenshqApplication.class, args);
  }
}
