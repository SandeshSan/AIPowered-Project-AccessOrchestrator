package com.accessorchestrator.agent;

import com.accessorchestrator.agent.AgentModels.AccessItem;
import com.accessorchestrator.agent.AgentModels.AdditionPreviewView;
import com.accessorchestrator.agent.AgentModels.AdditionResultView;
import com.accessorchestrator.agent.AgentModels.InFlightItem;
import com.accessorchestrator.dto.AdditionDtos.AdditionPreviewDto;
import com.accessorchestrator.dto.AdditionDtos.AdditionResultDto;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.service.ProjectAdditionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;


/**
 * Tools for adding a person to a project: admins, or managers for people up to three levels below them. Only the
 * membership is created; no access is requested (the person asks for it themselves). The model finds the person
 * and project, shows the preview and passes on the user's decision.
 */
@Component
public class ProjectAdditionTools {

    private final ProjectAdditionService additionService;

    public ProjectAdditionTools(ProjectAdditionService additionService) {
        this.additionService = additionService;
    }

    @Tool(description = "Preview adding a person to a project as a member: the required access they already have and "
            + "what they are missing (information only; nothing is requested). Only admins, and managers for people "
            + "who report to them, may add people. Always show this to the user before adding anyone.")
    public AdditionPreviewView previewAddToProject(
            @ToolParam(description = "User id of the person to add") String userId,
            @ToolParam(description = "projectId from findProject") Long projectId,
            @ToolParam(description = "Their role on the project; leave empty to use their job title", required = false)
            String projectRole,
            ToolContext toolContext) {
        return AgentTurn.run(toolContext, "previewAddToProject", turn -> {
            AdditionPreviewDto p = additionService.preview(turn.userId(), userId, projectId, projectRole);
            if (p.canProceed()) {
                turn.conversation().proposeAddition(p.employee().userId(), p.employee().name(), p.project().id(),
                        p.project().projectName(), p.projectRole(), turn.turn());
            }
            AdditionPreviewView view = new AdditionPreviewView(p.employee().userId(), p.employee().name(),
                    p.project().id(), p.project().projectName(), p.projectRole(), p.authorityBasis(),
                    p.alreadyHave().stream().map(e -> item(e, null)).toList(),
                    p.missingAccess().stream().map(pa -> item(pa.entitlement(), pa.reason())).toList(),
                    p.alreadyRequested().stream().map(o -> new InFlightItem(o.entitlementCode(),
                            o.igaRequestId() != null ? o.igaRequestId() : o.requestId(), o.status())).toList(),
                    p.blockers(), p.canProceed(),
                    p.canProceed() ? "Show the preview and ask the user to confirm. Do not call addEmployeeToProject "
                            + "until they confirm in a later message."
                            : "The addition cannot run: explain the blockers. Do not call addEmployeeToProject.");
            turn.additionPreview(view);
            return view;
        });
    }

    @Tool(description = "Add a person to a project as a member. No access is requested; they can ask the assistant "
            + "for the project's access themselves. Only call this after previewAddToProject was shown in an earlier "
            + "message AND the user explicitly confirmed in their latest message.")
    public AdditionResultView addEmployeeToProject(
            @ToolParam(description = "User id of the person to add") String userId,
            @ToolParam(description = "projectId from findProject") Long projectId,
            ToolContext toolContext) {
        return AgentTurn.run(toolContext, "addEmployeeToProject", turn -> {
            ConversationState.AdditionProposal proposal = turn.conversation().additionProposal();
            boolean presentedEarlier = proposal != null && proposal.targetUserId().equalsIgnoreCase(userId)
                    && proposal.projectId().equals(projectId) && proposal.turn() < turn.turn();
            if (!presentedEarlier) {
                throw new AgentGuardException("Refused: confirmation required. First call previewAddToProject, show "
                        + "the user what will be requested and ask them to confirm. Only call addEmployeeToProject "
                        + "after they confirm in a later message.");
            }
            if (!turn.explicitAddition() && !ConfirmationPolicy.isAffirmative(turn.userMessage())) {
                throw new AgentGuardException("Refused: the user's latest message is not an explicit confirmation. "
                        + "Ask them clearly whether to add " + proposal.targetName() + " to "
                        + proposal.projectName() + ".");
            }
            AdditionResultDto r = additionService.add(turn.userId(), userId, projectId, proposal.projectRole());
            turn.conversation().clearAdditionProposal();

            AdditionResultView view = new AdditionResultView(r.employee().userId(), r.employee().name(),
                    r.project().projectName(), r.projectRole(),
                    r.missingAccess().stream().map(pa -> item(pa.entitlement(), pa.reason())).toList());
            turn.addition(view);
            return view;
        });
    }

    private static AccessItem item(EntitlementDto e, String reason) {
        return new AccessItem(e.id(), e.application(), e.entitlementCode(), e.entitlementName(), e.riskLevel(), reason);
    }
}
