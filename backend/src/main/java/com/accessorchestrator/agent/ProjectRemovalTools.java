package com.accessorchestrator.agent;

import com.accessorchestrator.agent.AgentModels.AccessItem;
import com.accessorchestrator.agent.AgentModels.EmployeeView;
import com.accessorchestrator.agent.AgentModels.MemberView;
import com.accessorchestrator.agent.AgentModels.RemovalPreviewView;
import com.accessorchestrator.agent.AgentModels.RemovalResultView;
import com.accessorchestrator.agent.AgentModels.ReportView;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.RemovalDtos.RemovalPreviewDto;
import com.accessorchestrator.dto.RemovalDtos.RemovalResultDto;
import com.accessorchestrator.dto.UserAccessDto;
import com.accessorchestrator.service.AccessAuthority;
import com.accessorchestrator.service.ProjectRemovalService;
import com.accessorchestrator.service.UserService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools for leaving a project: anyone may remove themselves; admins may remove anyone; managers may remove people
 * up to three levels below them in the reporting line, from any of their projects.
 * The system decides what gets revoked (project access minus default access) and the IGA approves and
 * deprovisions it. The model only finds the person, shows the preview and passes on the user's decision.
 */
@Component
public class ProjectRemovalTools {

    private static final int MIN_REASON_LENGTH = 3;

    private final UserService userService;
    private final ProjectRemovalService removalService;
    private final AccessAuthority authority;

    public ProjectRemovalTools(UserService userService, ProjectRemovalService removalService,
                               AccessAuthority authority) {
        this.userService = userService;
        this.removalService = removalService;
        this.authority = authority;
    }

    @Tool(description = "Find employees by name or user id. Returns every match; if several people match, ask the "
            + "user which one they mean.")
    public List<EmployeeView> findEmployee(@ToolParam(description = "Name, part of a name, or user id") String query,
                                           ToolContext toolContext) {
        return AgentTurn.run(toolContext, "findEmployee", turn -> userService.search(query).stream()
                .map(u -> new EmployeeView(u.userId(), u.name(), u.role(), u.department(),
                        u.status() == UserStatus.ACTIVE))
                .toList());
    }

    @Tool(description = "List the active members of a project. Only admins may do this.")
    public List<MemberView> getProjectMembers(@ToolParam(description = "projectId from findProject") Long projectId,
                                              ToolContext toolContext) {
        return AgentTurn.run(toolContext, "getProjectMembers", turn -> removalService.members(turn.userId(), projectId)
                .stream()
                .map(m -> new MemberView(m.user().userId(), m.user().name(), m.projectRole(), m.joinedDate()))
                .toList());
    }

    @Tool(description = "List the people who report to the signed-in user (up to three levels down the reporting "
            + "line) and the projects each is on. A manager may remove these people from their projects.")
    public List<ReportView> getMyTeam(ToolContext toolContext) {
        return AgentTurn.run(toolContext, "getMyTeam", turn -> authority.reportsOf(userService.findUser(turn.userId()))
                .stream()
                .map(r -> new ReportView(r.user().userId(), r.user().name(), r.user().role(), r.level(),
                        r.user().managerName(), r.projects().stream().map(p -> p.projectName()).toList()))
                .toList());
    }

    @Tool(description = "Preview removing a person from a project: which access would be revoked, which default "
            + "access is kept, and which pending requests would be cancelled. The system checks permission: anyone "
            + "may remove themselves; admins may remove anyone; managers may remove people who report to them (up "
            + "to three levels down). Always show this to the user before removing anyone.")
    public RemovalPreviewView previewProjectRemoval(
            @ToolParam(description = "User id of the person to remove (the signed-in user's id for self-removal)")
            String userId,
            @ToolParam(description = "projectId from findProject") Long projectId,
            ToolContext toolContext) {
        return AgentTurn.run(toolContext, "previewProjectRemoval", turn -> {
            RemovalPreviewDto p = removalService.preview(turn.userId(), userId, projectId);
            if (p.canProceed()) {
                turn.conversation().proposeRemoval(p.employee().userId(), p.employee().name(), p.project().id(),
                        p.project().projectName(), turn.turn());
            }
            RemovalPreviewView view = new RemovalPreviewView(p.employee().userId(), p.employee().name(),
                    p.project().id(), p.project().projectName(), p.projectRole(), p.selfRemoval(),
                    p.authorityBasis(),
                    p.toRevoke().stream().map(r -> item(r.entitlement(), r.reason())).toList(),
                    p.keptDefault().stream().map(ProjectRemovalTools::defaultAccess).toList(),
                    p.requestsToCancel().stream().map(ProjectRemovalTools::shownId).toList(),
                    p.blockers(), p.canProceed(), nextStep(p));
            turn.removalPreview(view);
            return view;
        });
    }

    @Tool(description = "Remove a person from a project and ask the IGA to revoke their project access (a manager "
            + "must approve the revocation in the IGA). Only call this after previewProjectRemoval was shown in an "
            + "earlier message AND the user explicitly confirmed in their latest message AND gave a reason.")
    public RemovalResultView removeEmployeeFromProject(
            @ToolParam(description = "User id of the person to remove") String userId,
            @ToolParam(description = "projectId from findProject") Long projectId,
            @ToolParam(description = "The reason the user gave, in their words") String reason,
            ToolContext toolContext) {
        return AgentTurn.run(toolContext, "removeEmployeeFromProject", turn -> {
            ConversationState.RemovalProposal proposal = turn.conversation().removalProposal();
            // A reason the user typed into the confirm box is authoritative over the model's paraphrase
            String why = proposal != null && proposal.confirmedReason() != null ? proposal.confirmedReason() : reason;
            requireConfirmed(turn, userId, projectId, why);
            RemovalResultDto r = removalService.remove(turn.userId(), userId, projectId, why);
            turn.conversation().clearRemovalProposal();

            AccessRequestDto revocation = r.revocationRequest();
            RemovalResultView view = new RemovalResultView(r.employee().userId(), r.employee().name(),
                    r.project().projectName(), r.reason(),
                    revocation == null ? null : shownId(revocation),
                    revocation == null ? null : AgentModels.label(revocation.status(), revocation.type()),
                    revocation == null ? List.of() : revocation.items().stream()
                            .map(i -> item(i.entitlement(), i.reason())).toList(),
                    r.keptDefault().stream().map(ProjectRemovalTools::defaultAccess).toList(),
                    r.cancelledRequests().stream().map(ProjectRemovalTools::shownId).toList());
            turn.removal(view);
            return view;
        });
    }

    /** Same two keys as access requests: shown earlier by the system, confirmed now by the user. */
    private static void requireConfirmed(AgentTurn turn, String userId, Long projectId, String reason) {
        ConversationState.RemovalProposal proposal = turn.conversation().removalProposal();
        boolean presentedEarlier = proposal != null
                && proposal.targetUserId().equalsIgnoreCase(userId)
                && proposal.projectId().equals(projectId)
                && proposal.turn() < turn.turn();
        if (!presentedEarlier) {
            throw new AgentGuardException("Refused: confirmation required. First call previewProjectRemoval, show the "
                    + "user what will be revoked and ask them to confirm and give a reason. Only call "
                    + "removeEmployeeFromProject after they confirm in a later message.");
        }
        if (!turn.explicitRemoval() && !ConfirmationPolicy.isAffirmative(turn.userMessage())) {
            throw new AgentGuardException("Refused: the user's latest message is not an explicit confirmation. "
                    + "Ask them clearly whether to go ahead with the removal.");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new AgentGuardException("Refused: a reason is required. Ask the user why "
                    + proposal.targetName() + " is leaving " + proposal.projectName() + ".");
        }
    }

    private static String nextStep(RemovalPreviewDto p) {
        if (!p.canProceed()) {
            return "The removal cannot run yet: explain the blockers. Do not call removeEmployeeFromProject.";
        }
        return "Show what will be revoked and what default access is kept, then ask the user to confirm and give a "
                + "reason. Do not call removeEmployeeFromProject until they confirm in a later message.";
    }

    private static AccessItem item(EntitlementDto e, String reason) {
        return new AccessItem(e.id(), e.application(), e.entitlementCode(), e.entitlementName(), e.riskLevel(), reason);
    }

    private static AccessItem defaultAccess(UserAccessDto ua) {
        return item(ua.entitlement(), "Default access (kept)");
    }

    private static String shownId(AccessRequestDto r) {
        return r.igaRequestId() != null ? r.igaRequestId() : r.requestId();
    }
}
