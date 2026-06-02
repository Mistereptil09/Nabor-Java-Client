package tech.nabor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import tech.nabor.api.EventBus;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.api.model.sync.ResolvedConflict;
import tech.nabor.api.model.sync.SyncState;
import tech.nabor.ui.UiEvents;


public class SyncService {

    public enum Choice { LOCAL, REMOTE }

    private final SqliteRepository db;
    private final EventBus eventBus;

    public SyncService(PluginContext ctx) {
        this.db = ctx.getDb();
        this.eventBus = ctx.getEventBus();
    }


    public Optional<Instant> lastSync() {
        return db.syncState().get().map(SyncState::lastSyncedAt);
    }

    public int unsyncedChangesCount() {
        return db.syncChangelog().findUnsynced().size();
    }

    public List<PendingConflict> pendingConflicts() {
        return db.pendingConflicts().findAll();
    }

    public boolean hasConflicts() {
        return db.pendingConflicts().hasConflicts();
    }

    
    public List<PendingConflict> detectConflicts(Incident local, Incident remote, Instant lastSync) {
        List<PendingConflict> conflicts = new ArrayList<>();
        if (local == null || remote == null) {
            return conflicts;
        }
        boolean bothChanged = isAfter(local.updatedAt(), lastSync) && isAfter(remote.updatedAt(), lastSync);
        if (!bothChanged) {
            return conflicts;
        }
        addIfDifferent(conflicts, local.id(), "title", local.title(), remote.title());
        addIfDifferent(conflicts, local.id(), "description", local.description(), remote.description());
        addIfDifferent(conflicts, local.id(), "severity",
                name(local.severity()), name(remote.severity()));
        addIfDifferent(conflicts, local.id(), "status",
                name(local.status()), name(remote.status()));
        return conflicts;
    }

    private void addIfDifferent(List<PendingConflict> out, String rowId, String field,
                                String localVal, String remoteVal) {
        String l = localVal == null ? "" : localVal;
        String r = remoteVal == null ? "" : remoteVal;
        if (!l.equals(r)) {
            out.add(new PendingConflict(0, "incidents", rowId, field, l, r, Instant.now()));
        }
    }

    
    public void resolve(PendingConflict conflict, Choice choice) {
        db.resolvedConflicts().save(new ResolvedConflict(
                0, conflict.tableName(), conflict.rowId(), conflict.fieldName(),
                choice == Choice.LOCAL ? "local" : "remote", Instant.now()));

        if (choice == Choice.REMOTE && "incidents".equals(conflict.tableName())) {
            applyRemoteToIncident(conflict.rowId(), conflict.fieldName(), conflict.remoteValue());
            eventBus.publish(UiEvents.INCIDENTS_CHANGED, conflict.rowId());
        }
        db.pendingConflicts().delete(conflict.id());
        eventBus.publish(UiEvents.SYNC_CHANGED, conflict.rowId());
    }

    private void applyRemoteToIncident(String id, String field, String remoteValue) {
        Optional<Incident> found = db.incidents().findById(id);
        if (found.isEmpty()) {
            return;
        }
        Incident i = found.get();
        Incident updated = new Incident(
                i.id(), i.reporterId(), i.assignedTo(), i.neighbourhoodId(), i.mongoDocumentId(),
                field.equals("title") ? remoteValue : i.title(),
                field.equals("description") ? remoteValue : i.description(),
                field.equals("severity") ? IncidentSeverity.valueOf(remoteValue) : i.severity(),
                field.equals("status") ? IncidentStatus.valueOf(remoteValue) : i.status(),
                i.assignedAt(), i.createdAt(), Instant.now(), i.resolvedAt());
        db.incidents().save(updated);
    }

    public void markSynced() {
        db.syncChangelog().markAllSynced();
        String token = db.syncState().get().map(SyncState::lastSyncToken).orElse(null);
        db.syncState().save(new SyncState(Instant.now(), token, false));
        eventBus.publish(UiEvents.SYNC_CHANGED, null);
    }

   
    public boolean simulateRemoteConflict() {
        List<Incident> incidents = db.incidents().findByStatus(IncidentStatus.open, 1);
        if (incidents.isEmpty()) {
            incidents = db.incidents().findByStatus(IncidentStatus.in_progress, 1);
        }
        if (incidents.isEmpty()) {
            return false;
        }
        Incident local = incidents.get(0);
        String remoteStatus = local.status() == IncidentStatus.resolved
                ? IncidentStatus.open.name() : IncidentStatus.resolved.name();

        db.pendingConflicts().save(new PendingConflict(
                0, "incidents", local.id(), "status",
                local.status().name(), remoteStatus, Instant.now()));
        eventBus.publish(UiEvents.SYNC_CHANGED, local.id());
        return true;
    }


    private static boolean isAfter(Instant value, Instant reference) {
        if (value == null) {
            return false;
        }
        return reference == null || value.isAfter(reference);
    }

    private static String name(Enum<?> e) {
        return e == null ? "" : e.name();
    }
}
