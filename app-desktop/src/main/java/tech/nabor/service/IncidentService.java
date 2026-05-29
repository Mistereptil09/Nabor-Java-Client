package tech.nabor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;

/**
 * Logique métier des incidents (§7.1 — {@code IncidentService}).
 *
 * <p>S'appuie sur le repository SQLite local : consultation et création
 * fonctionnent hors-ligne (offline-first, §7.3). La synchronisation des
 * créations/éditions vers NestJS est gérée séparément (étape 9).</p>
 */
public class IncidentService {

    private static final int LIMIT = 200;

    private final SqliteRepository db;
    private final ConnectedUser user;

    public IncidentService(PluginContext ctx) {
        this.db = ctx.getDb();
        this.user = ctx.getConnectedUser();
    }

    /** Liste les incidents ; {@code statusFilter == null} = tous statuts confondus. */
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
            return cb.compareTo(ca); // plus récent d'abord
        });
        return all;
    }

    /** Crée un incident localement (statut {@code open}, signalé par l'utilisateur courant). */
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
        return incident;
    }

    public void assignToMe(String id) {
        db.incidents().assign(id, user.getUserId());
    }

    public void resolve(String id) {
        db.incidents().resolve(id);
    }
}
