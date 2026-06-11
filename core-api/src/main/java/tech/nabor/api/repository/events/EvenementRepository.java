package tech.nabor.api.repository.events;

import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.model.events.Evenement;

import java.util.List;
import java.util.Optional;

public interface EvenementRepository {
    List<Evenement> findAll();
    Optional<Evenement> findById(String id);
    List<Evenement> findByCreatorId(String creatorId);
    List<Evenement> findByNeighbourhood(String neighbourhoodId, EventStatus status, int limit);
    List<Evenement> findUpcoming(String neighbourhoodId, int limit); // starts_at > now, status = open
    List<Evenement> findByStatus(EventStatus status, int limit);
    void save(Evenement evenement);
    void delete(String id);                              // soft delete — changes deleted_at
}