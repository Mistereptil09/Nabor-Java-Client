package tech.nabor.api.repository.events;

import tech.nabor.api.model.enums.ParticipantStatus;
import tech.nabor.api.model.events.EventParticipant;

import java.util.List;
import java.util.Optional;

public interface EventParticipantRepository {
    Optional<EventParticipant> findByUserAndEvent(String userId, String eventId);
    List<EventParticipant> findByEventId(String eventId);
    List<EventParticipant> findByEventAndStatus(String eventId, ParticipantStatus status);
    List<EventParticipant> findByUserId(String userId);
    int countRegistered(String eventId);                 // status = registered
    void save(EventParticipant participant);
    void cancel(String userId, String eventId);          // changes cancelled_at
}