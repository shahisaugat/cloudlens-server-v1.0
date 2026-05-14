package com.cloudlenshq.server.github.repository;

import com.cloudlenshq.server.github.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    Optional<Deployment> findFirstByOwnerAndRepoAndEnvironmentOrderByDeployedAtDesc(String owner, String repo, String environment);
    java.util.List<Deployment> findAllByOrderByDeployedAtDesc();
}
