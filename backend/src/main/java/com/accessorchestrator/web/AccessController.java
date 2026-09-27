package com.accessorchestrator.web;

import com.accessorchestrator.dto.AccessAnalysisRequest;
import com.accessorchestrator.dto.AccessAnalysisResponse;
import com.accessorchestrator.dto.AccessComparisonResponse;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.CreateAccessRequestRequest;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.service.AccessAnalysisService;
import com.accessorchestrator.service.AccessRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/access")
public class AccessController {

    private final AccessAnalysisService accessAnalysisService;
    private final AccessRequestService accessRequestService;

    public AccessController(AccessAnalysisService accessAnalysisService, AccessRequestService accessRequestService) {
        this.accessAnalysisService = accessAnalysisService;
        this.accessRequestService = accessRequestService;
    }

    @PostMapping("/analyze")
    public AccessAnalysisResponse analyze(@Valid @RequestBody AccessAnalysisRequest request) {
        return accessAnalysisService.analyze(request.userId(), request.projectId());
    }

    /** Side-by-side comparison: GRANTED/MISSING per required entitlement, plus verification. */
    @PostMapping("/compare")
    public AccessComparisonResponse compare(@Valid @RequestBody AccessAnalysisRequest request) {
        return accessAnalysisService.compare(request.userId(), request.projectId());
    }

    @GetMapping("/missing")
    public List<ProjectAccessDto> missing(@RequestParam @NotBlank String userId,
                                          @RequestParam @Positive Long projectId) {
        return accessAnalysisService.calculateMissingAccess(userId, projectId);
    }

    @PostMapping("/requests")
    public ResponseEntity<AccessRequestDto> createRequest(@Valid @RequestBody CreateAccessRequestRequest request) {
        AccessRequestDto created = accessRequestService.createDraftRequest(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{requestId}").buildAndExpand(created.requestId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Requests for {@code userId}, or (with {@code createdBy}) requests someone started for other people. */
    @GetMapping("/requests")
    public List<AccessRequestDto> listRequests(@RequestParam(required = false) String userId,
                                               @RequestParam(required = false) String createdBy) {
        if (createdBy != null && !createdBy.isBlank()) {
            return accessRequestService.listRequestsCreatedBy(createdBy);
        }
        if (userId == null || userId.isBlank()) {
            throw new BusinessRuleException("userId or createdBy is required");
        }
        return accessRequestService.listRequestsForUser(userId);
    }

    @GetMapping("/requests/{requestId}")
    public AccessRequestDto getRequest(@PathVariable String requestId) {
        return accessRequestService.getRequest(requestId);
    }

    /** User confirmation: send a DRAFT request to the IGA for approval. */
    @PostMapping("/requests/{requestId}/submit")
    public AccessRequestDto submitRequest(@PathVariable String requestId) {
        return accessRequestService.submit(requestId);
    }

    /** Pull the latest status from the IGA now (the background poller does this periodically too). */
    @PostMapping("/requests/{requestId}/sync")
    public AccessRequestDto syncRequest(@PathVariable String requestId) {
        return accessRequestService.syncStatus(requestId);
    }
}
