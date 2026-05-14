package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findAllByUserOrderByStartedAtDesc(User user);
}
