package tech.nabor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.EventBus;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.ui.UiEvents;

import java.util.Map;


public class IncidentService {

    private static final int LIMIT = 200;

    private final SqliteRepository db;
    private final ConnectedUser user;
    private final EventBus eventBus;

    public IncidentService(PluginContext ctx) {
        this.db = ctx.getDb();
        this.user = ctx.getConnectedUser();
        this.eventBus = ctx.getEventBus();
    }

    public List<Incident> list(IncidentStatus statusFilter) {
        if (statusFilter != null) {
            return db.incidents().findByStatus(statusFilter, LIMIT);
        }
        List<Incident> all = new ArrayList<>();
        for (IncidentStatus status : IncidentStatus.values()) {
            all.addAll(db.incidents().findByStatus(status, LIMIT));
        }
        all.sort((a, b) -> {
            Instant ca = a.createdAt();
            Instant cb = b.createdAt();
            if (ca == null && cb == null) return 0;
            if (ca == null) return 1;
            if (cb == null) return -1;
            return cb.compareTo(ca); 
        });
        return all;
    }

    public Incident create(String title, String description, IncidentSeverity severity,
                            String neighbourhoodId) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                UUID.randomUUID().toString(),
                user.getUserId(),
                null, nidOrNull(neighbourhoodId), null,
                title, description,
                severity, IncidentStatus.open,
                null, now, now, null
        );
        db.incidents().save(incident);
        // Track in outbox for push — INSERT has no base_updated_at
        Map<String, String> newVals = new java.util.LinkedHashMap<>();
        newVals.put("title", title);
        if (description != null && !description.isBlank()) newVals.put("description", description);
        newVals.put("severity", severity.name());
        newVals.put("status", IncidentStatus.open.name());
        newVals.put("reporterId", user.getUserId());
        if (nidOrNull(neighbourhoodId) != null) newVals.put("neighbourhoodId", nidOrNull(neighbourhoodId));
        db.syncChangelog().track(new ChangeEvent(
                "incidents", incident.id(), "INSERT",
                null, newVals, null, now));
        eventBus.publish(UiEvents.INCIDENTS_CHANGED, incident.id());
        return incident;
    }

    public void assignToMe(String id) {
        var old = db.incidents().findById(id);
        db.incidents().assign(id, user.getUserId());
        trackUpdate(id, old.orElse(null), Map.of("assignedTo", user.getUserId(),
                "status", IncidentStatus.in_progress.name()));
        eventBus.publish(UiEvents.INCIDENTS_CHANGED, id);
    }

    public void resolve(String id) {
        var old = db.incidents().findById(id);
        db.incidents().resolve(id);
        trackUpdate(id, old.orElse(null), Map.of("status", IncidentStatus.resolved.name()));
        eventBus.publish(UiEvents.INCIDENTS_CHANGED, id);
    }

    private void trackUpdate(String id, Incident old, Map<String, String> newVals) {
        Map<String, String> prev = new java.util.HashMap<>();
        if (old != null) {
            if (old.assignedTo() != null) prev.put("assignedTo", old.assignedTo());
            prev.put("status", old.status().name());
        }
        String baseUpdatedAt = old != null && old.updatedAt() != null
                ? old.updatedAt().toString() : null;
        db.syncChangelog().track(new ChangeEvent(
                "incidents", id, "UPDATE", prev, newVals, baseUpdatedAt, Instant.now()));
    }

    private static String nidOrNull(String nid) {
        return nid != null && !nid.isBlank() ? nid : null;
    }
}
