package tech.nabor.api.repository.events;

import tech.nabor.api.model.enums.SwipeDirection;
import tech.nabor.api.model.events.EventSwipe;

import java.util.List;
import java.util.Optional;

public interface EventSwipeRepository {
    Optional<EventSwipe> findByUserAndEvent(String userId, String eventId);
    List<EventSwipe> findByUserAndDirection(String userId, SwipeDirection direction);
    void save(EventSwipe swipe);
}