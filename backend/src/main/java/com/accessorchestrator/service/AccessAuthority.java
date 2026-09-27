package com.accessorchestrator.service;

import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.RemovalDtos.ReportDto;
import com.accessorchestrator.exception.ForbiddenException;
import com.accessorchestrator.exception.ResourceNotFoundException;
import com.accessorchestrator.repository.ProjectMemberRepository;
import com.accessorchestrator.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Who may act on someone else's project membership, based on the reporting line:
 * <ul>
 *   <li>anyone may remove themselves;</li>
 *   <li>admins may remove anyone;</li>
 *   <li>an ACTIVE manager may remove people up to {@value #MAX_LEVELS} levels below them in the reporting line,
 *       from any of their projects.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AccessAuthority {

    /** A manager's manager's manager can still act; beyond that only admins. */
    public static final int MAX_LEVELS = 3;

    private final UserRepository users;
    private final ProjectMemberRepository members;

    public AccessAuthority(UserRepository users, ProjectMemberRepository members) {
        this.users = users;
        this.members = members;
    }

    public boolean canRemove(User actor, User target) {
        return isSelf(actor, target) || actor.isAdmin() || managementLevel(actor, target).isPresent();
    }

    /** Adding people to projects: admins, or managers up to {@value #MAX_LEVELS} levels above them. */
    public boolean canAdd(User actor, User target) {
        return actor.isAdmin() || managementLevel(actor, target).isPresent();
    }

    /** Viewing someone's access: themselves, admins, or managers up to {@value #MAX_LEVELS} levels above them. */
    public boolean canViewAccess(User actor, User target) {
        return isSelf(actor, target) || actor.isAdmin() || managementLevel(actor, target).isPresent();
    }

    /**
     * Loads both users inside this transaction (the reporting line is lazily loaded) and throws unless the actor
     * may view the target's access.
     */
    public void requireCanViewAccess(String actorUserId, String targetUserId) {
        User actor = users.findByUserIdIgnoreCase(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));
        User target = users.findByUserIdIgnoreCase(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));
        if (!canViewAccess(actor, target)) {
            throw new ForbiddenException("You can view your own access and that of people who report to you (up to "
                    + MAX_LEVELS + " levels down); " + target.getName() + " (" + target.getUserId()
                    + ") does not report to " + actor.getName() + ".");
        }
    }

    public boolean canListProjectMembers(User actor) {
        return actor.isAdmin();
    }

    /**
     * How many levels above {@code target} the actor sits (1 = direct manager), if within {@value #MAX_LEVELS}
     * and the actor is ACTIVE. Loop-safe even if bad data slipped past {@link User#setManager}.
     */
    public static Optional<Integer> managementLevel(User actor, User target) {
        if (actor.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }
        Set<String> seen = new HashSet<>();
        User m = target.getManager();
        for (int level = 1; m != null && level <= MAX_LEVELS && seen.add(m.getUserId()); level++) {
            if (m.getUserId().equalsIgnoreCase(actor.getUserId())) {
                return Optional.of(level);
            }
            m = m.getManager();
        }
        return Optional.empty();
    }

    /** Human-readable basis for a removal, e.g. "your direct report", used in previews. */
    public String basis(User actor, User target) {
        if (isSelf(actor, target)) {
            return "self-removal";
        }
        Optional<Integer> level = managementLevel(actor, target);
        if (level.isPresent()) {
            return level.get() == 1 ? "direct report" : "indirect report (" + level.get() + " levels down)";
        }
        return actor.isAdmin() ? "administrator" : "not permitted";
    }

    /** Everyone up to {@value #MAX_LEVELS} levels below the actor, with their active projects. */
    public List<ReportDto> reportsOf(User actor) {
        if (actor.getStatus() != UserStatus.ACTIVE) {
            return List.of();
        }
        List<ReportDto> reports = new ArrayList<>();
        List<User> level = List.of(actor);
        Set<Long> seen = new HashSet<>(Set.of(actor.getId()));
        for (int depth = 1; depth <= MAX_LEVELS && !level.isEmpty(); depth++) {
            List<User> next = users.findByManager_IdIn(level.stream().map(User::getId).toList()).stream()
                    .filter(u -> seen.add(u.getId()))
                    .sorted(Comparator.comparing(User::getName))
                    .toList();
            for (User u : next) {
                reports.add(new ReportDto(DtoMapper.toDto(u), depth,
                        members.findByUser_IdAndStatusOrderByJoinedDateDesc(u.getId(), MembershipStatus.ACTIVE).stream()
                                .map(m -> DtoMapper.toDto(m.getProject())).toList()));
            }
            level = next;
        }
        return reports;
    }

    private static boolean isSelf(User actor, User target) {
        return actor.getUserId().equalsIgnoreCase(target.getUserId());
    }
}
