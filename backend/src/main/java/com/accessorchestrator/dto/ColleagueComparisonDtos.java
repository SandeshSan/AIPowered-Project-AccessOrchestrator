package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RiskLevel;

import java.util.List;

/** Comparing the signed-in user's active access with a colleague's. */
public final class ColleagueComparisonDtos {

    private ColleagueComparisonDtos() {
    }

    /**
     * One entitlement held by only one of the two. A restricted entry (a colleague's high-risk access that the
     * viewer may not see in detail) carries only its application and risk level; id, code and name are null.
     *
     * @param requestableProjectId set when the viewer lacks it and it is part of the required access of one of the
     *                             viewer's active projects, with no request already in progress
     */
    public record ComparedEntitlement(
            Long entitlementId,
            String application,
            String entitlementCode,
            String entitlementName,
            RiskLevel riskLevel,
            boolean restricted,
            Long requestableProjectId,
            String requestableProjectName,
            String note) {
    }

    /**
     * @param fullDetail true when the viewer may see the colleague's access in full (their manager up the line, or
     *                   an admin); otherwise the colleague's high-risk entitlements are restricted
     */
    public record ColleagueComparisonDto(
            UserDto colleague,
            boolean fullDetail,
            List<ComparedEntitlement> colleagueOnly,
            List<ComparedEntitlement> youOnly,
            int inCommonCount) {
    }
}
