package tech.nabor.api.repository.events;

import tech.nabor.api.model.events.EventModerationAction;

import java.util.List;

public interface EventModerationActionRepository {
    List<EventModerationAction> findByEventId(String eventId);
    List<EventModerationAction> findByModeratorId(String moderatorId);
    void save(EventModerationAction action);
}