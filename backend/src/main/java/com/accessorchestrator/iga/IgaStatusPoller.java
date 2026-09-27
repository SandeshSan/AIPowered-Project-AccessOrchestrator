package com.accessorchestrator.iga;

import com.accessorchestrator.service.AccessRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps open requests in step with the IGA so UIs only need to poll this backend. Each request syncs in
 * its own transaction; one failure (e.g. IGA down) does not stop the others.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.iga.polling.enabled", havingValue = "true")
public class IgaStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(IgaStatusPoller.class);

    private final AccessRequestService accessRequestService;

    public IgaStatusPoller(AccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @Scheduled(fixedDelayString = "${app.iga.polling.interval:3s}", initialDelayString = "${app.iga.polling.interval:3s}")
    public void syncOpenRequests() {
        for (String requestId : accessRequestService.findOpenRequestIds()) {
            try {
                accessRequestService.syncStatus(requestId);
            } catch (RuntimeException e) {
                log.warn("Could not sync {}: {}", requestId, e.getMessage());
            }
        }
    }
}
