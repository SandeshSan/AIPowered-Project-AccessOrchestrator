package com.accessorchestrator.repository;

import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.UserAccess;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccessRepository extends JpaRepository<UserAccess, Long> {

    @EntityGraph(attributePaths = "entitlement")
    List<UserAccess> findByUser_Id(Long userPk);

    @EntityGraph(attributePaths = "entitlement")
    List<UserAccess> findByUser_IdAndStatus(Long userPk, AccessStatus status);

    Optional<UserAccess> findByUser_IdAndEntitlement_Id(Long userPk, Long entitlementPk);
}
