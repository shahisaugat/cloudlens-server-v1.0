package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.github.entity.CustomIntegration;
import com.cloudlenshq.server.github.entity.WebhookPayload;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookPayloadRepository extends JpaRepository<WebhookPayload, Long> {
    List<WebhookPayload> findAllByIntegrationOrderByReceivedAtDesc(CustomIntegration integration, Pageable pageable);
}
