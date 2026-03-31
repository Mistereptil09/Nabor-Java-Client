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
import tech.nabor.api.repository.social.*;
import tech.nabor.api.repository.sync.*;
import tech.nabor.api.repository.user.*;
import tech.nabor.app.db.DatabaseManager;
import tech.nabor.app.db.repository.incidents.*;
import tech.nabor.app.db.repository.local.*;
import tech.nabor.app.db.repository.messages.*;
import tech.nabor.app.db.repository.listings.*;
import tech.nabor.app.db.repository.events.*;
import tech.nabor.app.db.repository.polls.*;
import tech.nabor.app.db.repository.social.*;
import tech.nabor.app.db.repository.sync.*;
import tech.nabor.app.db.repository.user.*;

public class AppSqliteRepository implements SqliteRepository {

    private final Jdbi jdbi;

    // local
    private final LocalAccountRepository           localAccounts;
    private final AppLocaleConfigRepository localeConfigs;
    private final AppPluginStateRepository          pluginStates;
    private final AppPluginConfigRepository         pluginConfigs;

    // sync
    private final AppSyncChangelogRepository        syncChangelog;
    private final AppSyncStateRepository            syncState;
    private final AppPendingConflictRepository      pendingConflicts;
    private final AppResolvedConflictRepository     resolvedConflicts;

    // user
    private final AppUserRepository                 users;
    private final AppUserSessionRepository          userSessions;
    private final AppUserNotificationPreferencesRepository notificationPreferences;

    // social
    private final AppFollowRepository               follows;
    private final AppFriendshipRepository           friendships;
    private final AppUserBlockRepository            userBlocks;
    private final AppUserSwipeRepository            userSwipes;

    // messaging
    private final AppChatGroupRepository            chatGroups;
    private final AppUserInGroupRepository          usersInGroup;
    private final AppMessageMetadataRepository      messages;
    private final AppMessageReadReceiptRepository   readReceipts;

    // listing
    private final AppListingCategoryRepository      listingCategories;
    private final AppListingRepository              listings;
    private final AppListingTransactionRepository   listingTransactions;
    private final AppListingReportRepository        listingReports;
    private final AppListingModerationActionRepository listingModerationActions;

    // event
    private final AppEvenementCategoryRepository    evenementCategories;
    private final AppEvenementRepository            evenements;
    private final AppEventParticipantRepository     eventParticipants;
    private final AppEventSwipeRepository           eventSwipes;
    private final AppEventReportRepository          eventReports;
    private final AppEventModerationActionRepository eventModerationActions;

    // poll
    private final AppPollRepository                 polls;
    private final AppPollOptionRepository           pollOptions;
    private final AppVoteRepository                 votes;

    // incident
    private final AppIncidentRepository             incidents;

    public AppSqliteRepository(DatabaseManager db) {
        this.jdbi = db.getJdbi();

        // local
        this.localAccounts            = new AppLocalAccountRepository(jdbi);
        this.localeConfigs            = new AppLocaleConfigRepository(jdbi);
        this.pluginStates             = new AppPluginStateRepository(jdbi);
        this.pluginConfigs            = new AppPluginConfigRepository(jdbi);

        // sync
        this.syncChangelog            = new AppSyncChangelogRepository(jdbi);
        this.syncState                = new AppSyncStateRepository(jdbi);
        this.pendingConflicts         = new AppPendingConflictRepository(jdbi);
        this.resolvedConflicts        = new AppResolvedConflictRepository(jdbi);

        // user
        this.users                    = new AppUserRepository(jdbi);
        this.userSessions             = new AppUserSessionRepository(jdbi);
        this.notificationPreferences  = new AppUserNotificationPreferencesRepository(jdbi);

        // social
        this.follows                  = new AppFollowRepository(jdbi);
        this.friendships              = new AppFriendshipRepository(jdbi);
        this.userBlocks               = new AppUserBlockRepository(jdbi);
        this.userSwipes               = new AppUserSwipeRepository(jdbi);

        // messaging
        this.chatGroups               = new AppChatGroupRepository(jdbi);
        this.usersInGroup             = new AppUserInGroupRepository(jdbi);
        this.messages                 = new AppMessageMetadataRepository(jdbi);
        this.readReceipts             = new AppMessageReadReceiptRepository(jdbi);

        // listing
        this.listingCategories        = new AppListingCategoryRepository(jdbi);
        this.listings                 = new AppListingRepository(jdbi);
        this.listingTransactions      = new AppListingTransactionRepository(jdbi);
        this.listingReports           = new AppListingReportRepository(jdbi);
        this.listingModerationActions = new AppListingModerationActionRepository(jdbi);

        // event
        this.evenementCategories      = new AppEvenementCategoryRepository(jdbi);
        this.evenements               = new AppEvenementRepository(jdbi);
        this.eventParticipants        = new AppEventParticipantRepository(jdbi);
        this.eventSwipes              = new AppEventSwipeRepository(jdbi);
        this.eventReports             = new AppEventReportRepository(jdbi);
        this.eventModerationActions   = new AppEventModerationActionRepository(jdbi);

        // poll
        this.polls                    = new AppPollRepository(jdbi);
        this.pollOptions              = new AppPollOptionRepository(jdbi);
        this.votes                    = new AppVoteRepository(jdbi);

        // incident
        this.incidents                = new AppIncidentRepository(jdbi);
    }

    // ── local ─────────────────────────────────────────────────────────────────
    @Override public LocalAccountRepository            localAccounts()            { return localAccounts; }
    @Override public LocaleConfigRepository            localeConfigs()            { return localeConfigs; }
    @Override public PluginStateRepository             pluginStates()             { return pluginStates; }
    @Override public PluginConfigRepository            pluginConfigs()            { return pluginConfigs; }

    // ── sync ──────────────────────────────────────────────────────────────────
    @Override public SyncChangelogRepository           syncChangelog()            { return syncChangelog; }
    @Override public SyncStateRepository               syncState()                { return syncState; }
    @Override public AppPendingConflictRepository      pendingConflicts()         { return pendingConflicts; }
    @Override public AppResolvedConflictRepository     resolvedConflicts()        { return resolvedConflicts; }

    // ── user ──────────────────────────────────────────────────────────────────
    @Override public UserRepository                    users()                    { return users; }
    @Override public UserSessionRepository             userSessions()             { return userSessions; }
    @Override public UserNotificationPreferencesRepository notificationPreferences() { return notificationPreferences; }

    // ── social ────────────────────────────────────────────────────────────────
    @Override public FollowRepository                  follows()                  { return follows; }
    @Override public FriendshipRepository              friendships()              { return friendships; }
    @Override public UserBlockRepository               userBlocks()               { return userBlocks; }
    @Override public UserSwipeRepository               userSwipes()               { return userSwipes; }

    // ── messaging ─────────────────────────────────────────────────────────────
    @Override public ChatGroupRepository               chatGroups()               { return chatGroups; }
    @Override public UserInGroupRepository             usersInGroup()             { return usersInGroup; }
    @Override public MessageMetadataRepository         messages()                 { return messages; }
    @Override public MessageReadReceiptRepository      readReceipts()             { return readReceipts; }

    // ── listing ───────────────────────────────────────────────────────────────
    @Override public ListingCategoryRepository         listingCategories()        { return listingCategories; }
    @Override public ListingRepository                 listings()                 { return listings; }
    @Override public ListingTransactionRepository      listingTransactions()      { return listingTransactions; }
    @Override public ListingReportRepository           listingReports()           { return listingReports; }
    @Override public ListingModerationActionRepository listingModerationActions() { return listingModerationActions; }

    // ── event ─────────────────────────────────────────────────────────────────
    @Override public EvenementCategoryRepository       evenementCategories()      { return evenementCategories; }
    @Override public EvenementRepository               evenements()               { return evenements; }
    @Override public EventParticipantRepository        eventParticipants()        { return eventParticipants; }
    @Override public EventSwipeRepository              eventSwipes()              { return eventSwipes; }
    @Override public EventReportRepository             eventReports()             { return eventReports; }
    @Override public EventModerationActionRepository   eventModerationActions()  { return eventModerationActions; }

    // ── poll ──────────────────────────────────────────────────────────────────
    @Override public PollRepository polls()                    { return polls; }
    @Override public PollOptionRepository           pollOptions()              { return pollOptions; }
    @Override public VoteRepository                 votes()                    { return votes; }

    // ── incident ──────────────────────────────────────────────────────────────
    @Override public IncidentRepository incidents()                { return incidents; }
}