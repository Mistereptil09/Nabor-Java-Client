package tech.nabor.api.repository.events;

import tech.nabor.api.model.events.EventReport;

import java.util.List;

public interface EventReportRepository {
    List<EventReport> findByEventId(String eventId);
    List<EventReport> findUnresolved(int limit);
    void save(EventReport report);
    void resolve(String id);
}