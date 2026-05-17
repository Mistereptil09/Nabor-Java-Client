package tech.nabor.app;

import tech.nabor.api.SqliteRepository;
import tech.nabor.api.repository.events.*;
import tech.nabor.api.repository.incidents.*;
import tech.nabor.api.repository.listings.*;
import tech.nabor.api.repository.local.*;
import tech.nabor.api.repository.messages.*;
import tech.nabor.api.repository.polls.*;
import tech.nabor.api.repository.social.*;
import tech.nabor.api.repository.sync.*;
import tech.nabor.api.repository.user.*;

// Minimal mock implementation for testing plugins
public class MockSqliteRepository implements SqliteRepository {

    @Override
    public LocalAccountRepository localAccounts() {
        return null;
    }

    @Override
    public LocaleConfigRepository localeConfigs() {
        return null;
    }

    @Override
    public PluginStateRepository pluginStates() {
        return null;
    }

    @Override
    public PluginConfigRepository pluginConfigs() {
        return null;
    }

    @Override
    public UserRepository users() {
        // Return a mock that returns empty results
        return new UserRepository() {
            @Override
            public java.util.Optional<tech.nabor.api.model.user.User> findById(String id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<tech.nabor.api.model.user.User> findByEmail(String email) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<tech.nabor.api.model.user.User> findByNeighbourhood(String neighbourhoodId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<tech.nabor.api.model.user.User> findByRole(tech.nabor.api.model.enums.UserRole role) {
                return java.util.List.of();
            }

            @Override
            public void save(tech.nabor.api.model.user.User user) {
            }

            @Override
            public void delete(String id) {
            }
        };
    }

    @Override
    public UserSessionRepository userSessions() {
        return null;
    }

    @Override
    public UserNotificationPreferencesRepository notificationPreferences() {
        return null;
    }

    @Override
    public FollowRepository follows() {
        return null;
    }

    @Override
    public FriendshipRepository friendships() {
        return null;
    }

    @Override
    public UserBlockRepository userBlocks() {
        return null;
    }

    @Override
    public UserSwipeRepository userSwipes() {
        return null;
    }

    @Override
    public ChatGroupRepository chatGroups() {
        return null;
    }

    @Override
    public UserInGroupRepository usersInGroup() {
        return null;
    }

    @Override
    public MessageMetadataRepository messages() {
        return null;
    }

    @Override
    public MessageReadReceiptRepository readReceipts() {
        return null;
    }

    @Override
    public ListingCategoryRepository listingCategories() {
        return null;
    }

    @Override
    public ListingRepository listings() {
        return null;
    }

    @Override
    public ListingTransactionRepository listingTransactions() {
        return null;
    }

    @Override
    public ListingReportRepository listingReports() {
        return null;
    }

    @Override
    public ListingModerationActionRepository listingModerationActions() {
        return null;
    }

    @Override
    public EvenementCategoryRepository evenementCategories() {
        return null;
    }

    @Override
    public EvenementRepository evenements() {
        return null;
    }

    @Override
    public EventParticipantRepository eventParticipants() {
        return null;
    }

    @Override
    public EventSwipeRepository eventSwipes() {
        return null;
    }

    @Override
    public EventReportRepository eventReports() {
        return null;
    }

    @Override
    public EventModerationActionRepository eventModerationActions() {
        return null;
    }

    @Override
    public PollRepository polls() {
        return null;
    }

    @Override
    public PollOptionRepository pollOptions() {
        return null;
    }

    @Override
    public VoteRepository votes() {
        return null;
    }

    @Override
    public IncidentRepository incidents() {
        // Return a mock that returns empty results
        return new IncidentRepository() {
            @Override
            public java.util.List<tech.nabor.api.model.incidents.Incident> findByReporterId(String reporterId) {
                System.out.println("[MockDB] Querying incidents for reporter: " + reporterId);
                return java.util.List.of();
            }

            @Override
            public java.util.Optional<tech.nabor.api.model.incidents.Incident> findById(String id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<tech.nabor.api.model.incidents.Incident> findByAssignedTo(String userId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<tech.nabor.api.model.incidents.Incident> findByNeighbourhood(String neighbourhoodId, int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<tech.nabor.api.model.incidents.Incident> findByStatus(tech.nabor.api.model.enums.IncidentStatus status, int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<tech.nabor.api.model.incidents.Incident> findBySeverity(tech.nabor.api.model.enums.IncidentSeverity severity, int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<tech.nabor.api.model.incidents.Incident> findOpen(String neighbourhoodId, int limit) {
                return java.util.List.of();
            }

            @Override
            public void save(tech.nabor.api.model.incidents.Incident incident) {
            }

            @Override
            public void assign(String id, String userId) {
            }

            @Override
            public void resolve(String id) {
            }
        };
    }

    @Override
    public SyncChangelogRepository syncChangelog() {
        return null;
    }

    @Override
    public SyncStateRepository syncState() {
        return null;
    }

    @Override
    public PendingConflictRepository pendingConflicts() {
        return null;
    }

    @Override
    public ResolvedConflictRepository resolvedConflicts() {
        return null;
    }
}
