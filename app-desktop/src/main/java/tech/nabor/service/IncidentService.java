package tech.nabor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.EventBus;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.ui.UiEvents;


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

    public Incident create(String title, String description, IncidentSeverity severity) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                UUID.randomUUID().toString(),
                user.getUserId(),
                null, null, null,
                title, description,
                severity, IncidentStatus.open,
                null, now, now, null);
        db.incidents().save(incident);
        eventBus.publish(UiEvents.INCIDENTS_CHANGED, incident.id());
        return incident;
    }

    public void assignToMe(String id) {
        db.incidents().assign(id, user.getUserId());
        eventBus.publish(UiEvents.INCIDENTS_CHANGED, id);
    }

    public void resolve(String id) {
        db.incidents().resolve(id);
        eventBus.publish(UiEvents.INCIDENTS_CHANGED, id);
    }
}
