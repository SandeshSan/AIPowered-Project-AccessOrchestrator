package com.accessorchestrator.service;

import com.accessorchestrator.domain.AccessSource;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure, deterministic rule for what leaving a project revokes:
 * <b>revoke = (user's ACTIVE access ∩ project's access profile) − DEFAULT access</b>.
 * <p>
 * DEFAULT access is never revoked by a project removal. Access another of the user's projects
 * also needs is deliberately not considered yet.
 */
final class RevocationCalculator {

    /** @param reason the profile's reason the entitlement belonged to the project */
    record Revocation(UserAccessDto access, String reason) {
    }

    record Plan(List<Revocation> toRevoke, List<UserAccessDto> keptDefault) {
    }

    private RevocationCalculator() {
    }

    static Plan plan(Collection<ProjectAccessDto> projectProfile, Collection<UserAccessDto> activeAccess) {
        Map<Long, ProjectAccessDto> profile = projectProfile.stream()
                .collect(Collectors.toMap(pa -> pa.entitlement().id(), Function.identity(), (a, b) -> a));
        List<UserAccessDto> inProject = activeAccess.stream()
                .filter(ua -> profile.containsKey(ua.entitlement().id()))
                .sorted(AccessOrdering.USER_ACCESS)
                .toList();
        List<Revocation> revoke = inProject.stream()
                .filter(ua -> ua.source() != AccessSource.DEFAULT)
                .map(ua -> new Revocation(ua, profile.get(ua.entitlement().id()).reason()))
                .toList();
        List<UserAccessDto> kept = inProject.stream()
                .filter(ua -> ua.source() == AccessSource.DEFAULT)
                .toList();
        return new Plan(revoke, kept);
    }
}
