package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.auth.entity.User;
import com.cloudlenshq.server.github.entity.CustomIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomIntegrationRepository extends JpaRepository<CustomIntegration, Long> {
    List<CustomIntegration> findAllByUser(User user);
    Optional<CustomIntegration> findByUniqueId(String uniqueId);
}
