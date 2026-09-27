package com.accessorchestrator.repository;

import com.accessorchestrator.domain.ProjectAccess;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectAccessRepository extends JpaRepository<ProjectAccess, Long> {

    @EntityGraph(attributePaths = "entitlement")
    List<ProjectAccess> findByProject_Id(Long projectId);

    @EntityGraph(attributePaths = "entitlement")
    List<ProjectAccess> findByProject_IdAndRequiredTrue(Long projectId);
}
