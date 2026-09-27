package com.mockiga.api;

import com.mockiga.api.ApiModels.ActionRequest;
import com.mockiga.api.ApiModels.CreateRequest;
import com.mockiga.api.ApiModels.CreateResponse;
import com.mockiga.api.ApiModels.RequestView;
import com.mockiga.domain.IgaRequestSnapshot;
import com.mockiga.domain.IgaRequestStatus;
import com.mockiga.service.IgaRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/mock-iga/access-requests")
public class AccessRequestController {

    private static final ActionRequest NO_ACTION_BODY = new ActionRequest(null, null);

    private final IgaRequestService service;

    public AccessRequestController(IgaRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(@Valid @RequestBody CreateRequest body) {
        IgaRequestSnapshot created = service.create(body.type(), body.userId(), body.entitlements(),
                body.externalReference(), body.requestedBy(), body.justification());
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                        .buildAndExpand(created.requestId()).toUri())
                .body(new CreateResponse(created.requestId(), created.status()));
    }

    @GetMapping
    public List<RequestView> list(@RequestParam(required = false) IgaRequestStatus status) {
        return service.list(status).stream().map(RequestView::of).toList();
    }

    @GetMapping("/{requestId}")
    public RequestView get(@PathVariable String requestId) {
        return RequestView.of(service.get(requestId));
    }

    @PostMapping("/{requestId}/approve")
    public RequestView approve(@PathVariable String requestId,
                               @Valid @RequestBody(required = false) ActionRequest body) {
        ActionRequest b = body == null ? NO_ACTION_BODY : body;
        return RequestView.of(service.approve(requestId, b.actor(), b.comment()));
    }

    @PostMapping("/{requestId}/reject")
    public RequestView reject(@PathVariable String requestId,
                              @Valid @RequestBody(required = false) ActionRequest body) {
        ActionRequest b = body == null ? NO_ACTION_BODY : body;
        return RequestView.of(service.reject(requestId, b.actor(), b.comment()));
    }

    /** Withdraw a request that has not started provisioning (used by the orchestrator, not the manager). */
    @PostMapping("/{requestId}/cancel")
    public RequestView cancel(@PathVariable String requestId,
                              @Valid @RequestBody(required = false) ActionRequest body) {
        ActionRequest b = body == null ? NO_ACTION_BODY : body;
        return RequestView.of(service.cancel(requestId, b.actor(), b.comment()));
    }

    @PostMapping("/{requestId}/provision")
    public RequestView provision(@PathVariable String requestId,
                                 @Valid @RequestBody(required = false) ActionRequest body) {
        ActionRequest b = body == null ? NO_ACTION_BODY : body;
        return RequestView.of(service.provision(requestId, b.actor()));
    }
}
