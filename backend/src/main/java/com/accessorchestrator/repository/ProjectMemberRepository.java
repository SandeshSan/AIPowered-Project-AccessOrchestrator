package com.accessorchestrator.repository;

import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    @EntityGraph(attributePaths = "project")
    List<ProjectMember> findByUser_IdAndStatusOrderByJoinedDateDesc(Long userPk, MembershipStatus status);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProject_IdAndStatusOrderByJoinedDateAsc(Long projectPk, MembershipStatus status);

    @EntityGraph(attributePaths = {"user", "project"})
    Optional<ProjectMember> findByUser_IdAndProject_IdAndStatus(Long userPk, Long projectPk, MembershipStatus status);

    /** Any membership row, active or ended. */
    Optional<ProjectMember> findByUser_IdAndProject_Id(Long userPk, Long projectPk);
}
