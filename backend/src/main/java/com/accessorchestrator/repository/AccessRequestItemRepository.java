package com.accessorchestrator.repository;

import com.accessorchestrator.domain.AccessRequestItem;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AccessRequestItemRepository extends JpaRepository<AccessRequestItem, Long> {

    /** One row per entitlement a user already has in flight (request in one of the given statuses). */
    record OpenItem(Long entitlementId, String entitlementCode, String requestId, String igaRequestId,
                    RequestStatus status, RequestType type) {
    }

    @Query("""
            select new com.accessorchestrator.repository.AccessRequestItemRepository$OpenItem(
                   e.id, e.entitlementCode, r.requestId, r.igaRequestId, r.status, r.type)
            from AccessRequestItem i join i.accessRequest r join i.entitlement e
            where r.user.id = :userPk and r.status in :statuses
            order by r.createdAt""")
    List<OpenItem> findOpenItems(Long userPk, Collection<RequestStatus> statuses);
}
