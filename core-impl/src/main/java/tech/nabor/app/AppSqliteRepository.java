package tech.nabor.app;

import org.jdbi.v3.core.Jdbi;
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
import tech.nabor.app.db.DatabaseManager;
import tech.nabor.app.db.repository.events.*;
import tech.nabor.app.db.repository.incidents.*;
import tech.nabor.app.db.repository.listings.*;
import tech.nabor.app.db.repository.local.*;
import tech.nabor.app.db.repository.messages.*;
import tech.nabor.app.db.repository.polls.*;
import tech.nabor.app.db.repository.social.*;
import tech.nabor.app.db.repository.sync.*;
import tech.nabor.app.db.repository.user.*;

public class AppSqliteRepository implements SqliteRepository {

    private final Jdbi jdbi;

    // Local tables
    private final LocalAccountRepository localAccounts;
    private final LocaleConfigRepository localeConfigs;
    private final PluginStateRepository pluginStates;
    private final PluginConfigRepository pluginConfigs;

    // Users
    private final UserRepository users;
    private final UserSessionRepository userSessions;
    private final UserNotificationPreferencesRepository notificationPreferences;

    // Social
    private final FollowRepository follows;
    private final FriendshipRepository friendships;
    private final UserBlockRepository userBlocks;
    private final UserSwipeRepository userSwipes;

    // Messages
    private final ChatGroupRepository chatGroups;
    private final UserInGroupRepository usersInGroup;
    private final MessageMetadataRepository messages;
    private final MessageReadReceiptRepository readReceipts;

    // Listings
    private final ListingCategoryRepository listingCategories;
    private final ListingRepository listings;
    private final ListingTransactionRepository listingTransactions;
    private final ListingReportRepository listingReports;
    private final ListingModerationActionRepository listingModerationActions;

    // Events
    private final EvenementCategoryRepository evenementCategories;
    private final EvenementRepository evenements;
    private final EventParticipantRepository eventParticipants;
    private final EventSwipeRepository eventSwipes;
    private final EventReportRepository eventReports;
    private final EventModerationActionRepository eventModerationActions;

    // Polls
    private final PollRepository polls;
    private final PollOptionRepository pollOptions;
    private final VoteRepository votes;

    // Incidents
    private final IncidentRepository incidents;

    // Sync
    private final SyncChangelogRepository syncChangelog;
    private final SyncStateRepository syncState;
    private final PendingConflictRepository pendingConflicts;
    private final ResolvedConflictRepository resolvedConflicts;

    public AppSqliteRepository(DatabaseManager db) {
        this.jdbi = db.getJdbi();

        // Local
        this.localAccounts = new AppLocalAccountRepository(jdbi);
        this.localeConfigs = new AppLocaleConfigRepositoryImpl(jdbi);
        this.pluginStates = new AppPluginStateRepository(jdbi);
        this.pluginConfigs = new AppPluginConfigRepository(jdbi);

        // Users
        this.users = new AppUserRepository(jdbi);
        this.userSessions = new AppUserSessionRepository(jdbi);
        this.notificationPreferences = new AppUserNotificationPreferencesRepository(jdbi);

        // Social
        this.follows = new AppFollowRepository(jdbi);
        this.friendships = new AppFriendshipRepository(jdbi);
        this.userBlocks = new AppUserBlockRepository(jdbi);
        this.userSwipes = new AppUserSwipeRepository(jdbi);

        // Messages
        this.chatGroups = new AppChatGroupRepository(jdbi);
        this.usersInGroup = new AppUserInGroupRepository(jdbi);
        this.messages = new AppMessageMetadataRepository(jdbi);
        this.readReceipts = new AppMessageReadReceiptRepository(jdbi);

        // Listings
        this.listingCategories = new AppListingCategoryRepository(jdbi);
        this.listings = new AppListingRepository(jdbi);
        this.listingTransactions = new AppListingTransactionRepository(jdbi);
        this.listingReports = new AppListingReportRepository(jdbi);
        this.listingModerationActions = new AppListingModerationActionRepository(jdbi);

        // Events
        this.evenementCategories = new AppEvenementCategoryRepository(jdbi);
        this.evenements = new AppEvenementRepository(jdbi);
        this.eventParticipants = new AppEventParticipantRepository(jdbi);
        this.eventSwipes = new AppEventSwipeRepository(jdbi);
        this.eventReports = new AppEventReportRepository(jdbi);
        this.eventModerationActions = new AppEventModerationActionRepository(jdbi);

        // Polls
        this.polls = new AppPollRepository(jdbi);
        this.pollOptions = new AppPollOptionRepository(jdbi);
        this.votes = new AppVoteRepository(jdbi);

        // Incidents
        this.incidents = new AppIncidentRepository(jdbi);

        // Sync
        this.syncChangelog = new AppSyncChangelogRepository(jdbi);
        this.syncState = new AppSyncStateRepository(jdbi);
        this.pendingConflicts = new AppPendingConflictRepository(jdbi);
        this.resolvedConflicts = new AppResolvedConflictRepository(jdbi);
    }

    // Local tables
    @Override public LocalAccountRepository localAccounts() { return localAccounts; }
    @Override public LocaleConfigRepository localeConfigs() { return localeConfigs; }
    @Override public PluginStateRepository pluginStates() { return pluginStates; }
    @Override public PluginConfigRepository pluginConfigs() { return pluginConfigs; }

    // Users
    @Override public UserRepository users() { return users; }
    @Override public UserSessionRepository userSessions() { return userSessions; }
    @Override public UserNotificationPreferencesRepository notificationPreferences() { return notificationPreferences; }

    // Social
    @Override public FollowRepository follows() { return follows; }
    @Override public FriendshipRepository friendships() { return friendships; }
    @Override public UserBlockRepository userBlocks() { return userBlocks; }
    @Override public UserSwipeRepository userSwipes() { return userSwipes; }

    // Messages
    @Override public ChatGroupRepository chatGroups() { return chatGroups; }
    @Override public UserInGroupRepository usersInGroup() { return usersInGroup; }
    @Override public MessageMetadataRepository messages() { return messages; }
    @Override public MessageReadReceiptRepository readReceipts() { return readReceipts; }

    // Listings
    @Override public ListingCategoryRepository listingCategories() { return listingCategories; }
    @Override public ListingRepository listings() { return listings; }
    @Override public ListingTransactionRepository listingTransactions() { return listingTransactions; }
    @Override public ListingReportRepository listingReports() { return listingReports; }
    @Override public ListingModerationActionRepository listingModerationActions() { return listingModerationActions; }

    // Events
    @Override public EvenementCategoryRepository evenementCategories() { return evenementCategories; }
    @Override public EvenementRepository evenements() { return evenements; }
    @Override public EventParticipantRepository eventParticipants() { return eventParticipants; }
    @Override public EventSwipeRepository eventSwipes() { return eventSwipes; }
    @Override public EventReportRepository eventReports() { return eventReports; }
    @Override public EventModerationActionRepository eventModerationActions() { return eventModerationActions; }

    // Polls
    @Override public PollRepository polls() { return polls; }
    @Override public PollOptionRepository pollOptions() { return pollOptions; }
    @Override public VoteRepository votes() { return votes; }

    // Incidents
    @Override public IncidentRepository incidents() { return incidents; }

    // Sync
    @Override public SyncChangelogRepository syncChangelog() { return syncChangelog; }
    @Override public SyncStateRepository syncState() { return syncState; }
    @Override public PendingConflictRepository pendingConflicts() { return pendingConflicts; }
    @Override public ResolvedConflictRepository resolvedConflicts() { return resolvedConflicts; }
}