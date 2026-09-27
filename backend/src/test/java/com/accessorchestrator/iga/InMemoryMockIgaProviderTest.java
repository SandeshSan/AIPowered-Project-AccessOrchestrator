package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMockIgaProviderTest {

    private final IgaProvider provider = new InMemoryMockIgaProvider();

    @Test
    void submittedRequestAwaitsApproval() {
        AccessRequestResponse response = provider.createAccessRequest(new IgaAccessRequest("REQ-1", com.accessorchestrator.domain.RequestType.GRANT, "NT10036", "NT10036",
                "john@novatech.example", "NOVATECH", "Joined project",
                List.of(new IgaAccessRequest.Item("VPN", "NOVATECH_VPN", "network"))));

        assertThat(response.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        assertThat(provider.getRequestStatus(response.igaRequestId()).status())
                .isEqualTo(RequestStatus.PENDING_APPROVAL);
    }

    @Test
    void unknownRequestIsNotFound() {
        assertThatThrownBy(() -> provider.getRequestStatus("IGA-0"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
