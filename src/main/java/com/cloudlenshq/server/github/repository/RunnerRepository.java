package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.github.entity.Runner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunnerRepository extends JpaRepository<Runner, Long> {
    Optional<Runner> findByName(String name);
}
