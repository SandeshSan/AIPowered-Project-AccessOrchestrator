package com.accessorchestrator.agent;

import com.accessorchestrator.exception.BusinessRuleException;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Server-side state of one chat conversation. The agent cannot create or alter a proposal except by calling
 * {@code calculateMissingAccess}, whose result is computed in Java; only the user (through the UI) can narrow
 * it to a selection.
 */
public final class ConversationState {

    /**
     * What was shown to the user as requestable (in display order), and in which turn.
     *
     * @param selectedIds the subset the user picked in the UI, or null when nothing was picked (= all)
     */
    public record Proposal(Long projectId, String projectName, List<Long> entitlementIds, int turn,
                           List<Long> selectedIds) {

        /** The entitlement ids a confirmation may submit: the user's selection, or everything proposed. */
        public List<Long> submittable() {
            return selectedIds != null ? selectedIds : entitlementIds;
        }

        public boolean isNarrowed() {
            return selectedIds != null && selectedIds.size() < entitlementIds.size();
        }
    }

    /**
     * A removal shown to the user by previewProjectRemoval, awaiting their confirmation.
     *
     * @param confirmedReason set when the user confirmed with the UI button (authoritative reason), else null
     */
    public record RemovalProposal(String targetUserId, String targetName, Long projectId, String projectName,
                                  int turn, String confirmedReason) {
    }

    /** Adding a person to a project, shown by previewAddToProject and awaiting confirmation. */
    public record AdditionProposal(String targetUserId, String targetName, Long projectId, String projectName,
                                   String projectRole, int turn) {
    }

    /** Whole turns kept for the model; trimmed by turn so a tool call is never separated from its result. */
    private static final int MAX_REMEMBERED_TURNS = 10;

    private final String id;
    private final String userId;
    private final Deque<List<Message>> turns = new ArrayDeque<>();
    private int turn;
    private Proposal proposal;
    private RemovalProposal removalProposal;
    private AdditionProposal additionProposal;
    /** Turn of the latest preview batch: everything previewed in the same turn stays pending together. */
    private int batchTurn;
    private Instant lastActive = Instant.now();

    ConversationState(String id, String userId) {
        this.id = id;
        this.userId = userId;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    synchronized int nextTurn() {
        lastActive = Instant.now();
        return ++turn;
    }

    synchronized Instant lastActive() {
        return lastActive;
    }

    public synchronized Proposal proposal() {
        return proposal;
    }

    /**
     * Records what was presented. An identical proposal keeps its original turn (and any selection) so that
     * re-running the analysis in the confirmation turn does not reset the confirmation window.
     */
    synchronized void propose(Long projectId, String projectName, Set<Long> entitlementIds, int currentTurn) {
        if (entitlementIds.isEmpty()) {
            if (proposal != null && proposal.projectId().equals(projectId)) {
                proposal = null;
            }
            return;
        }
        if (proposal != null && proposal.projectId().equals(projectId)
                && Set.copyOf(proposal.entitlementIds()).equals(entitlementIds)) {
            enterBatch(currentTurn, proposal);
            return;
        }
        enterBatch(currentTurn, null);
        proposal = new Proposal(projectId, projectName, List.copyOf(entitlementIds), currentTurn, null);
    }

    /**
     * The user picked a subset of the proposed entitlements in the UI. Kept in proposal order; anything not
     * on offer is rejected rather than silently dropped.
     */
    synchronized void select(List<Long> selectedIds) {
        if (proposal == null) {
            throw new BusinessRuleException("There is no proposed access to select from; ask the assistant first");
        }
        List<Long> unknown = selectedIds.stream().filter(id -> !proposal.entitlementIds().contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("Selected entitlements were not proposed: " + unknown);
        }
        List<Long> ordered = proposal.entitlementIds().stream().filter(selectedIds::contains).toList();
        proposal = new Proposal(proposal.projectId(), proposal.projectName(), proposal.entitlementIds(),
                proposal.turn(), ordered);
    }

    synchronized void clearProposal() {
        proposal = null;
    }

    public synchronized RemovalProposal removalProposal() {
        return removalProposal;
    }

    /** Same person and project keeps the original turn, so a re-preview does not reset the confirmation window. */
    synchronized void proposeRemoval(String targetUserId, String targetName, Long projectId, String projectName,
                                     int currentTurn) {
        if (removalProposal != null && removalProposal.targetUserId().equalsIgnoreCase(targetUserId)
                && removalProposal.projectId().equals(projectId)) {
            enterBatch(currentTurn, removalProposal);
            return;
        }
        enterBatch(currentTurn, null);
        removalProposal = new RemovalProposal(targetUserId, targetName, projectId, projectName, currentTurn, null);
    }

    /** The UI's confirm button: explicit confirmation of the pending removal with the user's own reason. */
    synchronized void confirmRemoval(String reason) {
        if (removalProposal == null) {
            throw new BusinessRuleException("There is no pending removal to confirm; ask the assistant first");
        }
        removalProposal = new RemovalProposal(removalProposal.targetUserId(), removalProposal.targetName(),
                removalProposal.projectId(), removalProposal.projectName(), removalProposal.turn(), reason);
    }

    synchronized void clearRemovalProposal() {
        removalProposal = null;
    }

    public synchronized AdditionProposal additionProposal() {
        return additionProposal;
    }

    /** Same person and project keeps the original turn, so a re-preview does not reset the confirmation window. */
    synchronized void proposeAddition(String targetUserId, String targetName, Long projectId, String projectName,
                                      String projectRole, int currentTurn) {
        if (additionProposal != null && additionProposal.targetUserId().equalsIgnoreCase(targetUserId)
                && additionProposal.projectId().equals(projectId)) {
            enterBatch(currentTurn, additionProposal);
            return;
        }
        enterBatch(currentTurn, null);
        additionProposal = new AdditionProposal(targetUserId, targetName, projectId, projectName, projectRole,
                currentTurn);
    }

    synchronized void clearAdditionProposal() {
        additionProposal = null;
    }

    /** The UI's confirm button for an addition; fails if nothing is pending. */
    synchronized void requireAdditionPending() {
        if (additionProposal == null) {
            throw new BusinessRuleException("There is no pending addition to confirm; ask the assistant first");
        }
    }

    /**
     * Previews shown in the same turn form one batch that stays pending together (e.g. "move Elena from Orion to
     * Novatech" = a removal and an addition). The first preview of a later turn starts a new batch and drops the
     * older pending actions, so a "yes" never confirms something stale. {@code keep} survives (a re-preview).
     */
    private void enterBatch(int currentTurn, Object keep) {
        if (currentTurn <= batchTurn) {
            return;
        }
        if (proposal != keep) {
            proposal = null;
        }
        if (removalProposal != keep) {
            removalProposal = null;
        }
        if (additionProposal != keep) {
            additionProposal = null;
        }
        batchTurn = currentTurn;
    }

    /** Pending actions, in a stable order: ACCESS_REQUEST, PROJECT_REMOVAL, PROJECT_ADDITION. */
    synchronized List<String> pendingActions() {
        List<String> pending = new ArrayList<>();
        if (proposal != null) {
            pending.add("ACCESS_REQUEST");
        }
        if (removalProposal != null) {
            pending.add("PROJECT_REMOVAL");
        }
        if (additionProposal != null) {
            pending.add("PROJECT_ADDITION");
        }
        return pending;
    }

    /** "No" withdraws whatever was on offer. */
    synchronized void clearPendingActions() {
        proposal = null;
        removalProposal = null;
        additionProposal = null;
    }

    /** Earlier turns, oldest first: user message, assistant tool calls, tool results, final reply. */
    synchronized List<Message> history() {
        return turns.stream().flatMap(List::stream).toList();
    }

    synchronized void remember(List<Message> turnMessages) {
        turns.addLast(List.copyOf(turnMessages));
        while (turns.size() > MAX_REMEMBERED_TURNS) {
            turns.removeFirst();
        }
    }
}
