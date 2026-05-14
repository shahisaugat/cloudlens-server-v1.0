package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.github.entity.Integration;
import com.cloudlenshq.server.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {
    List<Integration> findAllByUser(User user);
    Optional<Integration> findByUserAndType(User user, String type);
}
