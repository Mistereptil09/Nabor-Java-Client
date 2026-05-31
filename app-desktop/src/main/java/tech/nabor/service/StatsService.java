package tech.nabor.service;

import java.util.LinkedHashMap;
import java.util.Map;

import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.events.Evenement;


public class StatsService {

    private static final int LIMIT = 500;

    private final SqliteRepository db;

    public StatsService(PluginContext ctx) {
        this.db = ctx.getDb();
    }

    public Map<IncidentStatus, Integer> incidentsByStatus() {
        Map<IncidentStatus, Integer> counts = new LinkedHashMap<>();
        for (IncidentStatus status : IncidentStatus.values()) {
            counts.put(status, db.incidents().findByStatus(status, LIMIT).size());
        }
        return counts;
    }

    public Map<IncidentSeverity, Integer> incidentsBySeverity() {
        Map<IncidentSeverity, Integer> counts = new LinkedHashMap<>();
        for (IncidentSeverity severity : IncidentSeverity.values()) {
            counts.put(severity, db.incidents().findBySeverity(severity, LIMIT).size());
        }
        return counts;
    }

    public int totalIncidents() {
        return incidentsByStatus().values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalEvents() {
        int total = 0;
        for (EventStatus status : EventStatus.values()) {
            total += db.evenements().findByStatus(status, LIMIT).size();
        }
        return total;
    }

    public int totalRegistrations() {
        int total = 0;
        for (EventStatus status : EventStatus.values()) {
            for (Evenement event : db.evenements().findByStatus(status, LIMIT)) {
                total += db.eventParticipants().countRegistered(event.id());
            }
        }
        return total;
    }
}
