package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.github.entity.SlackWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SlackWorkspaceRepository extends JpaRepository<SlackWorkspace, Long> {
    Optional<SlackWorkspace> findByTeamId(String teamId);
    Optional<SlackWorkspace> findByUser(com.cloudlenshq.server.auth.entity.User user);
}
