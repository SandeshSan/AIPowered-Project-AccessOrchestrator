package com.accessorchestrator.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Turns a failed AI provider call into a message fit for an employee. The provider's own error text (which can
 * include part of the API key, the vendor's wording and links) is never passed on; callers log it instead.
 */
final class AiProviderFailures {

    /** Spring AI reports HTTP errors as e.g. {@code "HTTP 401 - {...provider body...}"}. */
    private static final Pattern HTTP_STATUS = Pattern.compile("^HTTP (\\d{3})\\b");

    static final String REJECTED_CREDENTIALS = "The AI service rejected the assistant's credentials. "
            + "Please contact your administrator.";
    static final String MISCONFIGURED = "The AI service rejected the assistant's configuration. "
            + "Please contact your administrator.";
    static final String BUSY = "The AI service is busy or its usage limit has been reached. "
            + "Please try again in a few minutes.";
    static final String BAD_REQUEST = "The AI service could not process this message. "
            + "Please try again, or start a new conversation.";
    static final String UNAVAILABLE = "The AI service is unavailable right now. Please try again in a moment.";

    private AiProviderFailures() {
    }

    static AgentUnavailableException toUserFacing(RuntimeException e) {
        Integer status = httpStatus(e);
        if (status == null) {
            return new AgentUnavailableException(UNAVAILABLE, false, e);
        }
        return switch (status) {
            // Wrong or revoked key, or no access to the model: an admin has to fix it, retrying won't help
            case 401, 403 -> new AgentUnavailableException(REJECTED_CREDENTIALS, true, e);
            // Unknown model name or wrong base URL
            case 404 -> new AgentUnavailableException(MISCONFIGURED, true, e);
            case 429 -> new AgentUnavailableException(BUSY, false, e);
            case 400, 413, 422 -> new AgentUnavailableException(BAD_REQUEST, false, e);
            default -> new AgentUnavailableException(UNAVAILABLE, false, e);
        };
    }

    /** The provider's HTTP status, if the failure (or its cause) carries one; null for timeouts and the like. */
    static Integer httpStatus(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof RestClientResponseException rce) {
                return rce.getStatusCode().value();
            }
            if (t instanceof ResourceAccessException) {
                return null;
            }
            if (t.getMessage() != null) {
                Matcher m = HTTP_STATUS.matcher(t.getMessage());
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }
        }
        return null;
    }
}
