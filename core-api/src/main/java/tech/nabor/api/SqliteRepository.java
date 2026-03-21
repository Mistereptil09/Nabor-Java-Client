package tech.nabor.api;

import tech.nabor.api.repository.events.*;
import tech.nabor.api.repository.incidents.*;
import tech.nabor.api.repository.listings.*;
import tech.nabor.api.repository.messages.*;
import tech.nabor.api.repository.polls.*;
import tech.nabor.api.repository.social.*;
import tech.nabor.api.repository.sync.*;
import tech.nabor.api.repository.user.*;
import tech.nabor.api.repository.local.*;

public interface SqliteRepository {
    // local tables
    LocalAccountRepository localAccounts();
    AppLocaleConfigRepository localeConfigs();
    PluginStateRepository pluginStates();
    PluginConfigRepository pluginConfigs();

    // -- Api DB
    // users & auth
    UserRepository users();
    UserSessionRepository userSessions();
    UserNotificationPreferencesRepository notificationPreferences();

    // social
    FollowRepository follows();
    FriendshipRepository friendships();
    UserBlockRepository userBlocks();
    UserSwipeRepository userSwipes();

    // messages
    ChatGroupRepository chatGroups();
    UserInGroupRepository usersInGroup();
    MessageMetadataRepository messages();
    MessageReadReceiptRepository readReceipts();

    // listings
    ListingCategoryRepository listingCategories();
    ListingRepository listings();
    ListingTransactionRepository listingTransactions();
    ListingReportRepository listingReports();
    ListingModerationActionRepository listingModerationActions();

    // events
    EvenementCategoryRepository evenementCategories();
    EvenementRepository evenements();
    EventParticipantRepository eventParticipants();
    EventSwipeRepository eventSwipes();
    EventReportRepository eventReports();
    EventModerationActionRepository eventModerationActions();

    // polls
    PollRepository polls();
    PollOptionRepository pollOptions();
    VoteRepository votes();

    // incidents
    IncidentRepository incidents();

    // sync db
    SyncChangelogRepository syncChangelog();
    SyncStateRepository syncState();             // for last_synced_at and others.
    PendingConflictRepository pendingConflicts();
    ResolvedConflictRepository resolvedConflicts();
}