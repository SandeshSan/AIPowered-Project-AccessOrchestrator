package com.accessorchestrator.repository;

import com.accessorchestrator.domain.AccessRequest;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    @EntityGraph(attributePaths = {"user", "project", "items", "items.entitlement"})
    Optional<AccessRequest> findByRequestId(String requestId);

    @EntityGraph(attributePaths = {"user", "project", "items", "items.entitlement"})
    Optional<AccessRequest> findByIgaRequestId(String igaRequestId);

    @EntityGraph(attributePaths = {"user", "project"})
    List<AccessRequest> findByUser_UserIdIgnoreCaseOrderByCreatedAtDesc(String userId);

    /** Requests someone started on another person's behalf (e.g. a manager's removals). */
    @EntityGraph(attributePaths = {"user", "project"})
    List<AccessRequest> findByCreatedByIgnoreCaseOrderByCreatedAtDesc(String createdBy);

    @EntityGraph(attributePaths = {"user", "project", "items", "items.entitlement"})
    List<AccessRequest> findByUser_IdAndProject_IdAndTypeAndStatusIn(Long userPk, Long projectPk, RequestType type,
                                                                     Collection<RequestStatus> statuses);

    /** Submitted requests still moving through the IGA workflow. */
    @Query("select r.requestId from AccessRequest r where r.igaRequestId is not null and r.status in :statuses")
    List<String> findOpenRequestIds(Collection<RequestStatus> statuses);
}
