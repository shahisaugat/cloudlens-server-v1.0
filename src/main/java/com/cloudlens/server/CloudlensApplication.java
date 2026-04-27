package com.cloudlens.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class CloudlensApplication {
  public static void main(String[] args) {
    SpringApplication.run(CloudlensApplication.class, args);
  }

  @GetMapping("/api/health")
  public String checkHealth() {
    return "Good";
  }
}
