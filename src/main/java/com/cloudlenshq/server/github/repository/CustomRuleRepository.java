package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.github.entity.CustomIntegration;
import com.cloudlenshq.server.github.entity.CustomRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomRuleRepository extends JpaRepository<CustomRule, Long> {
    List<CustomRule> findAllByIntegration(CustomIntegration integration);
}
