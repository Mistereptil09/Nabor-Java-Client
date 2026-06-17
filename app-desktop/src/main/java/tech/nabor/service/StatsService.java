package tech.nabor.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.incidents.Incident;


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

    private List<Incident> allIncidents() {
        List<Incident> all = new ArrayList<>();
        for (IncidentStatus status : IncidentStatus.values()) {
            all.addAll(db.incidents().findByStatus(status, LIMIT));
        }
        return all;
    }

    
    public java.util.Optional<Duration> averageResolutionTime() {
        return averageDuration(allIncidents(), Incident::createdAt, Incident::resolvedAt);
    }

    public java.util.Optional<Duration> averagePickupTime() {
        return averageDuration(allIncidents(), Incident::createdAt, Incident::assignedAt);
    }

    private java.util.Optional<Duration> averageDuration(
            List<Incident> incidents,
            java.util.function.Function<Incident, Instant> from,
            java.util.function.Function<Incident, Instant> to) {
        long count = 0;
        long totalSeconds = 0;
        for (Incident incident : incidents) {
            Instant start = from.apply(incident);
            Instant end = to.apply(incident);
            if (start != null && end != null && !end.isBefore(start)) {
                totalSeconds += Duration.between(start, end).getSeconds();
                count++;
            }
        }
        if (count == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Duration.ofSeconds(totalSeconds / count));
    }

    public double incidentHoursThisWeek() {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        long totalSeconds = 0;
        for (Incident incident : allIncidents()) {
            Instant start = incident.createdAt();
            if (start == null) {
                continue;
            }
            Instant end = incident.resolvedAt() != null ? incident.resolvedAt() : now;
            // Intersection [start, end] ∩ [weekAgo, now]
            Instant clippedStart = start.isAfter(weekAgo) ? start : weekAgo;
            Instant clippedEnd = end.isBefore(now) ? end : now;
            if (clippedEnd.isAfter(clippedStart)) {
                totalSeconds += Duration.between(clippedStart, clippedEnd).getSeconds();
            }
        }
        return totalSeconds / 3600.0;
    }

   
    public record DailyCount(LocalDate day, int created, int resolved) {}

  
    public List<DailyCount> creationVsResolutionTrend(int days) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate start = today.minusDays(days - 1L);

        Map<LocalDate, int[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            byDay.put(start.plusDays(i), new int[2]); 
        }

        for (Incident incident : allIncidents()) {
            if (incident.createdAt() != null) {
                LocalDate d = incident.createdAt().atZone(zone).toLocalDate();
                int[] slot = byDay.get(d);
                if (slot != null) {
                    slot[0]++;
                }
            }
            if (incident.resolvedAt() != null) {
                LocalDate d = incident.resolvedAt().atZone(zone).toLocalDate();
                int[] slot = byDay.get(d);
                if (slot != null) {
                    slot[1]++;
                }
            }
        }

        List<DailyCount> trend = new ArrayList<>();
        byDay.forEach((day, slot) -> trend.add(new DailyCount(day, slot[0], slot[1])));
        return trend;
    }


    public Map<IncidentSeverity, Optional<Duration>> resolutionTimeBySeverity() {
        Map<IncidentSeverity, Optional<Duration>> result = new LinkedHashMap<>();
        for (IncidentSeverity severity : IncidentSeverity.values()) {
            List<Incident> ofSeverity = db.incidents().findBySeverity(severity, LIMIT);
            result.put(severity, averageDuration(ofSeverity, Incident::createdAt, Incident::resolvedAt));
        }
        return result;
    }
}
