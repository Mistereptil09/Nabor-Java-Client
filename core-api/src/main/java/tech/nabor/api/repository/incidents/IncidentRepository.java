package tech.nabor.api.repository.incidents;

import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository {
    List<Incident> findAll();
    Optional<Incident> findById(String id);
    List<Incident> findByReporterId(String reporterId);
    List<Incident> findByAssignedTo(String userId);
    List<Incident> findByNeighbourhood(String neighbourhoodId, int limit);
    List<Incident> findByStatus(IncidentStatus status, int limit);
    List<Incident> findBySeverity(IncidentSeverity severity, int limit);
    List<Incident> findOpen(String neighbourhoodId, int limit);  // status = open or in_progress
    void save(Incident incident);
    void delete(String id);                              // hard delete (not tracked)
    void assign(String id, String userId);
    void resolve(String id);
}