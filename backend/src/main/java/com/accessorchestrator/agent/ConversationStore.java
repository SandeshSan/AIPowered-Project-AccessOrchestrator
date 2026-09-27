package com.accessorchestrator.agent;

import com.accessorchestrator.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory conversations (POC). Idle conversations expire; each is bound to the user who started it. */
@Component
public class ConversationStore {

    private static final Duration IDLE_TIMEOUT = Duration.ofHours(2);

    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();

    /** Continues {@code conversationId} if it exists and belongs to {@code userId}; otherwise starts a new one. */
    public ConversationState getOrStart(String conversationId, String userId) {
        evictIdle();
        if (StringUtils.hasText(conversationId)) {
            ConversationState existing = conversations.get(conversationId);
            if (existing != null) {
                if (!existing.userId().equalsIgnoreCase(userId)) {
                    // Do not reveal that someone else's conversation exists
                    throw new ResourceNotFoundException("Conversation", conversationId);
                }
                return existing;
            }
        }
        String id = UUID.randomUUID().toString();
        ConversationState created = new ConversationState(id, userId);
        conversations.put(id, created);
        return created;
    }

    private void evictIdle() {
        Instant cutoff = Instant.now().minus(IDLE_TIMEOUT);
        conversations.values().removeIf(c -> c.lastActive().isBefore(cutoff));
    }
}
