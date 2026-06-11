-- ============================================================
-- UTILISATEURS
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
                       id                       TEXT    PRIMARY KEY,
                       first_name               TEXT    NOT NULL,
                       last_name                TEXT    NOT NULL,
                       email                    TEXT    UNIQUE NOT NULL,
                       password_hash            TEXT    NOT NULL,
                       totp_secret              TEXT,   -- TODO remove this
                       stripe_account_id        TEXT    UNIQUE,
                       neighbourhood_id         TEXT,
                       visibility               TEXT    NOT NULL DEFAULT 'public'
                           CHECK (visibility IN ('public','friends','private')),
                       bio                      TEXT,
                       message_policy           TEXT    NOT NULL DEFAULT 'open'
                           CHECK (message_policy IN ('open','filtered','closed')),
                       locale                   TEXT    NOT NULL DEFAULT 'fr',
                       profile_picture_mongo_id TEXT,
                       banner_mongo_id          TEXT,
                       role                     TEXT    NOT NULL DEFAULT 'resident'
                           CHECK (role IN ('resident','neighbourhood_rep','moderator','admin')),
                       last_login_at            INTEGER,
                       password_changed_at      INTEGER,
                       created_at               INTEGER NOT NULL,
                       updated_at               INTEGER,
                       deleted_at               INTEGER
);

CREATE TABLE IF NOT EXISTS user_sessions (
                               id                 TEXT    PRIMARY KEY,
                               user_id            TEXT    NOT NULL REFERENCES users(id),
                               refresh_token_hash TEXT    NOT NULL UNIQUE,
                               device_name        TEXT,
                               ip_address         TEXT,
                               user_agent         TEXT,
                               last_used_at       INTEGER NOT NULL,
                               expires_at         INTEGER NOT NULL,
                               revoked_at         INTEGER
);

CREATE TABLE IF NOT EXISTS user_notification_preferences (
                                               user_id            TEXT    PRIMARY KEY REFERENCES users(id),
                                               notif_new_follower INTEGER NOT NULL DEFAULT 1,
                                               notif_new_listing  INTEGER NOT NULL DEFAULT 1,
                                               notif_new_event    INTEGER NOT NULL DEFAULT 1,
                                               notif_new_poll     INTEGER NOT NULL DEFAULT 1,
                                               notif_waitlist     INTEGER NOT NULL DEFAULT 1,
                                               notif_message      INTEGER NOT NULL DEFAULT 1,
                                               updated_at         INTEGER
);

-- ============================================================
-- MAPPING QUARTIER
-- ============================================================
CREATE TABLE IF NOT EXISTS mapping_neighbourhood_id (
    neighbourhood_id   TEXT NOT NULL PRIMARY KEY,
    neighbourhood_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_whitelist (
    entity_type TEXT NOT NULL,
    field_name  TEXT NOT NULL,
    PRIMARY KEY (entity_type, field_name)
);

-- ============================================================
-- RÉSEAU SOCIAL
-- ============================================================

CREATE TABLE IF NOT EXISTS follow (
                        follower_id TEXT    NOT NULL REFERENCES users(id),
                        followed_id TEXT    NOT NULL REFERENCES users(id),
                        followed_at INTEGER NOT NULL,
                        PRIMARY KEY (follower_id, followed_id)
);

CREATE TABLE IF NOT EXISTS friendships (
                             id            TEXT    PRIMARY KEY,
                             user1_id      TEXT    NOT NULL REFERENCES users(id),
                             user2_id      TEXT    NOT NULL REFERENCES users(id),
                             friended_at   INTEGER NOT NULL,
                             unfriended_at INTEGER,
                             group_id      TEXT    REFERENCES chat_groups(id)
);

CREATE TABLE IF NOT EXISTS user_blocks (
                             blocker_id TEXT    NOT NULL REFERENCES users(id),
                             blocked_id TEXT    NOT NULL REFERENCES users(id),
                             blocked_at INTEGER NOT NULL,
                             PRIMARY KEY (blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS user_swipes (
                             swiper_id TEXT    NOT NULL REFERENCES users(id),
                             swiped_id TEXT    NOT NULL REFERENCES users(id),
                             direction TEXT    NOT NULL CHECK (direction IN ('like','dislike')),
                             swiped_at INTEGER NOT NULL,
                             PRIMARY KEY (swiper_id, swiped_id)
);

-- ============================================================
-- MESSAGERIE
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_groups (
                             id          TEXT    PRIMARY KEY,
                             name        TEXT,
                             description TEXT,
                             created_by  TEXT    NOT NULL REFERENCES users(id),
                             type        TEXT    NOT NULL CHECK (type IN ('direct_message','group_chat','neighbourhood')),
                             listing_id  TEXT    REFERENCES listings(id),
                             created_at  INTEGER NOT NULL,
                             updated_at  INTEGER,
                             deleted_at  INTEGER
);

CREATE TABLE IF NOT EXISTS users_in_group (
                                user_id       TEXT    NOT NULL REFERENCES users(id),
                                group_id      TEXT    NOT NULL REFERENCES chat_groups(id),
                                role_in_group TEXT    NOT NULL DEFAULT 'message'
                                    CHECK (role_in_group IN ('watch','message','actions','admin')),
                                joined_at     INTEGER NOT NULL,
                                left_at       INTEGER,
                                kicked_at     INTEGER,
                                is_muted      INTEGER NOT NULL DEFAULT 0,
                                muted_until   INTEGER,
                                PRIMARY KEY (user_id, group_id)
);

CREATE TABLE IF NOT EXISTS message_metadata (
                                  id                TEXT    PRIMARY KEY,
                                  mongo_message_id  TEXT    NOT NULL,
                                  group_id          TEXT    NOT NULL REFERENCES chat_groups(id),
                                  sender_id         TEXT    NOT NULL REFERENCES users(id),
                                  sent_at           INTEGER NOT NULL,
                                  edited_at         INTEGER,
                                  is_deleted        INTEGER NOT NULL DEFAULT 0,
                                  deleted_at        INTEGER,
                                  parent_message_id TEXT    REFERENCES message_metadata(id)
);

CREATE TABLE IF NOT EXISTS message_read_receipts (
                                       message_id TEXT    NOT NULL REFERENCES message_metadata(id),
                                       user_id    TEXT    NOT NULL REFERENCES users(id),
                                       read_at    INTEGER NOT NULL,
                                       PRIMARY KEY (message_id, user_id)
);

-- ============================================================
-- ANNONCES
-- ============================================================

CREATE TABLE IF NOT EXISTS listing_category (
                                  id              INTEGER PRIMARY KEY AUTOINCREMENT,
                                  parent_category INTEGER REFERENCES listing_category(id),
                                  category_name   TEXT    NOT NULL,
                                  created_at      INTEGER NOT NULL,
                                  updated_at      INTEGER
);

CREATE TABLE IF NOT EXISTS listings (
                          id                TEXT    PRIMARY KEY,
                          creator_id        TEXT    NOT NULL REFERENCES users(id),
                          title             TEXT    NOT NULL,
                          description       TEXT,
                          category_id       INTEGER REFERENCES listing_category(id),
                          listing_type      TEXT    NOT NULL CHECK (listing_type IN ('offer','request')),
                          price_cents       INTEGER NOT NULL DEFAULT 0,
                          status            TEXT    NOT NULL DEFAULT 'open'
                              CHECK (status IN ('open','pending','in_progress','closed','cancelled')),
                          neighbourhood_id  TEXT,
                          mongo_document_id TEXT,
                          created_at        INTEGER NOT NULL,
                          updated_at        INTEGER,
                          closed_at         INTEGER,
                          deleted_at        INTEGER
);

CREATE TABLE IF NOT EXISTS listing_transactions (
                                      id                    TEXT    PRIMARY KEY,
                                      listing_id            TEXT    NOT NULL REFERENCES listings(id),
                                      provider_id           TEXT    NOT NULL REFERENCES users(id),
                                      requester_id          TEXT    NOT NULL REFERENCES users(id),
                                      amount_cents          INTEGER NOT NULL DEFAULT 0,
                                      commission_cents      INTEGER NOT NULL DEFAULT 0,
                                      stripe_session_id     TEXT    UNIQUE,
                                      stripe_payment_intent TEXT    UNIQUE,
                                      contract_mongo_id     TEXT,
                                      receipt_mongo_id      TEXT,
                                      payment_failed_reason TEXT,
                                      status                TEXT    NOT NULL DEFAULT 'pending'
                                          CHECK (status IN ('pending','completed','payment_failed','cancelled')),
                                      created_at            INTEGER NOT NULL,
                                      paid_at               INTEGER,
                                      completed_at          INTEGER,
                                      cancelled_at          INTEGER
);

CREATE TABLE IF NOT EXISTS listing_reports (
                                 id          TEXT    PRIMARY KEY,
                                 listing_id  TEXT    NOT NULL REFERENCES listings(id),
                                 reporter_id TEXT    NOT NULL REFERENCES users(id),
                                 reason      TEXT    NOT NULL,
                                 created_at  INTEGER NOT NULL,
                                 resolved_at INTEGER
);

CREATE TABLE IF NOT EXISTS listing_moderation_actions (
                                            id           TEXT    PRIMARY KEY,
                                            listing_id   TEXT    NOT NULL REFERENCES listings(id),
                                            moderator_id TEXT    NOT NULL REFERENCES users(id),
                                            action       TEXT    NOT NULL CHECK (action IN ('cancelled','warned','restored')),
    reason       TEXT    NOT NULL,
    created_at   INTEGER NOT NULL
);

-- ============================================================
-- ÉVÉNEMENTS
-- ============================================================

CREATE TABLE IF NOT EXISTS evenements_category (
                                     id              INTEGER PRIMARY KEY AUTOINCREMENT,
                                     parent_category INTEGER REFERENCES evenements_category(id),
                                     category_name   TEXT    NOT NULL,
                                     created_at      INTEGER NOT NULL,
                                     updated_at      INTEGER
);

CREATE TABLE IF NOT EXISTS evenements (
                            id                    TEXT    PRIMARY KEY,
                            creator_id            TEXT    NOT NULL REFERENCES users(id),
                            neighbourhood_id      TEXT,
                            category_id           INTEGER REFERENCES evenements_category(id),
                            group_id              TEXT    REFERENCES chat_groups(id),
                            title                 TEXT    NOT NULL,
                            status                TEXT    NOT NULL DEFAULT 'draft'
                                CHECK (status IN ('draft','published','open','cancelled','completed')),
                            invite_code           TEXT,
                            cost_cents            INTEGER NOT NULL DEFAULT 0,
                            starts_at             INTEGER,
                            ends_at               INTEGER,
                            max_participants      INTEGER,
                            refund_deadline_hours INTEGER NOT NULL DEFAULT 48,
                            mongo_document_id     TEXT,
                            published_at          INTEGER,
                            cancelled_at          INTEGER,
                            completed_at          INTEGER,
                            created_at            INTEGER NOT NULL,
                            updated_at            INTEGER,
                            deleted_at            INTEGER
);

CREATE TABLE IF NOT EXISTS event_participants (
                                    user_id               TEXT    NOT NULL REFERENCES users(id),
                                    event_id              TEXT    NOT NULL REFERENCES evenements(id),
                                    status                TEXT    NOT NULL DEFAULT 'waitlisted'
                                        CHECK (status IN ('registered','waitlisted','cancelled')),
                                    payment_status        TEXT    NOT NULL DEFAULT 'free'
                                        CHECK (payment_status IN ('free','pending','completed','refunded')),
                                    stripe_session_id     TEXT    UNIQUE,
                                    stripe_payment_intent TEXT    UNIQUE,
                                    amount_cents          INTEGER NOT NULL DEFAULT 0,
                                    registered_at         INTEGER NOT NULL,
                                    promoted_at           INTEGER,
                                    paid_at               INTEGER,
                                    cancelled_at          INTEGER,
                                    notified_at           INTEGER,
                                    refunded_at           INTEGER,
                                    refund_stripe_id      TEXT,
                                    PRIMARY KEY (user_id, event_id)
);

CREATE TABLE IF NOT EXISTS event_swipes (
                              user_id   TEXT    NOT NULL REFERENCES users(id),
                              event_id  TEXT    NOT NULL REFERENCES evenements(id),
                              direction TEXT    NOT NULL CHECK (direction IN ('like','dislike')),
                              swiped_at INTEGER NOT NULL,
                              PRIMARY KEY (user_id, event_id)
);

CREATE TABLE IF NOT EXISTS event_reports (
                               id          TEXT    PRIMARY KEY,
                               event_id    TEXT    NOT NULL REFERENCES evenements(id),
                               reporter_id TEXT    NOT NULL REFERENCES users(id),
                               reason      TEXT    NOT NULL,
                               created_at  INTEGER NOT NULL,
                               resolved_at INTEGER
);

CREATE TABLE IF NOT EXISTS event_moderation_actions (
                                          id           TEXT    PRIMARY KEY,
                                          event_id     TEXT    NOT NULL REFERENCES evenements(id),
                                          moderator_id TEXT    NOT NULL REFERENCES users(id),
                                          action       TEXT    NOT NULL CHECK (action IN ('cancelled','warned','restored')),
    reason       TEXT    NOT NULL,
    created_at   INTEGER NOT NULL
);

-- ============================================================
-- SONDAGES
-- ============================================================

CREATE TABLE IF NOT EXISTS polls (
                       id               TEXT    PRIMARY KEY,
                       title            TEXT    NOT NULL,
                       description      TEXT,
                       creator_id       TEXT    NOT NULL REFERENCES users(id),
                       neighbourhood_id TEXT,
                       poll_type        TEXT    NOT NULL DEFAULT 'single'
                           CHECK (poll_type IN ('single','multiple','weighted')),
                       starts_at        INTEGER,
                       ends_at          INTEGER,
                       is_anonymous     INTEGER NOT NULL DEFAULT 0,
                       closed_at        INTEGER,
                       closed_by        TEXT    REFERENCES users(id),
                       created_at       INTEGER NOT NULL,
                       updated_at       INTEGER,
                       deleted_at       INTEGER
);

CREATE TABLE IF NOT EXISTS poll_options (
                              id         TEXT    PRIMARY KEY,
                              poll_id    TEXT    NOT NULL REFERENCES polls(id),
                              label      TEXT    NOT NULL,
                              created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS votes (
                       user_id    TEXT    NOT NULL REFERENCES users(id),
                       option_id  TEXT    NOT NULL REFERENCES poll_options(id),
                       weight     INTEGER NOT NULL DEFAULT 1,
                       voted_at   INTEGER NOT NULL,
                       updated_at INTEGER,
                       PRIMARY KEY (user_id, option_id)
);

-- ============================================================
-- INCIDENTS
-- ============================================================

CREATE TABLE IF NOT EXISTS incidents (
                           id                TEXT    PRIMARY KEY,
                           reporter_id       TEXT    NOT NULL REFERENCES users(id),
                           assigned_to       TEXT    REFERENCES users(id),
                           neighbourhood_id  TEXT,
                           mongo_document_id TEXT,
                           title             TEXT    NOT NULL,
                           description       TEXT,
                           severity          TEXT    NOT NULL DEFAULT 'medium'
                               CHECK (severity IN ('low','medium','high','critical')),
                           status            TEXT    NOT NULL DEFAULT 'open'
                               CHECK (status IN ('open','in_progress','resolved')),
                           assigned_at       INTEGER,
                           created_at        INTEGER NOT NULL,
                           updated_at        INTEGER,
                           resolved_at       INTEGER
);

-- ============================================================
-- TABLES LOCALES (spécifiques à l'app Java)
-- ============================================================

CREATE TABLE IF NOT EXISTS local_accounts (
                                user_id       TEXT    PRIMARY KEY,
                                email         TEXT    NOT NULL,
                                display_name  TEXT    NOT NULL,
                                is_active     INTEGER NOT NULL DEFAULT 0,
                                last_login_at INTEGER,
                                refresh_token TEXT     -- for auto-login between reboots
);

CREATE TABLE IF NOT EXISTS app_locale_config (
                                   user_id    TEXT    PRIMARY KEY REFERENCES local_accounts(user_id),
                                   locale     TEXT    NOT NULL DEFAULT 'fr',
                                   updated_at INTEGER
);

CREATE TABLE IF NOT EXISTS plugin_state (
                              user_id       TEXT    NOT NULL REFERENCES local_accounts(user_id),
                              plugin_id     TEXT    NOT NULL,
                              enabled       INTEGER NOT NULL DEFAULT 1,
                              display_order INTEGER NOT NULL DEFAULT 0,
                              updated_at    INTEGER,
                              PRIMARY KEY (user_id, plugin_id)
);

CREATE TABLE IF NOT EXISTS plugin_config (
                               user_id    TEXT    NOT NULL REFERENCES local_accounts(user_id),
                               plugin_id  TEXT    NOT NULL,
                               key        TEXT    NOT NULL,
                               value      TEXT,
                               updated_at INTEGER,
                               PRIMARY KEY (user_id, plugin_id, key)
);

CREATE TABLE IF NOT EXISTS sync_state (
                            id                 INTEGER PRIMARY KEY CHECK (id = 1),
                            latest_sync_cursor TEXT,
                            resume_cursor      TEXT,
                            is_rolling_back    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sync_changelog (
                                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                                table_name      TEXT    NOT NULL,
                                row_id          TEXT    NOT NULL,
                                operation       TEXT    NOT NULL CHECK (operation IN ('INSERT','UPDATE','DELETE')),
                                changed_fields  TEXT,
                                previous_values TEXT,
                                new_values      TEXT,
                                base_updated_at TEXT,
                                changed_at      INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS pending_conflicts (
                                   id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                   table_name     TEXT    NOT NULL,
                                   row_id         TEXT    NOT NULL,
                                   field_name     TEXT,            -- NULL = whole-record conflict
                                   local_value    TEXT    NOT NULL,  -- JSON: client_data from conflict response
                                   remote_value   TEXT    NOT NULL,  -- JSON: server_data from conflict response
                                   detected_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS resolved_conflicts (
                                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                                    table_name   TEXT    NOT NULL,
                                    row_id       TEXT    NOT NULL,
                                    field_name   TEXT    NOT NULL,
                                    chosen_value TEXT    NOT NULL, -- "local" or "remote"
                                    resolved_at  INTEGER NOT NULL
);