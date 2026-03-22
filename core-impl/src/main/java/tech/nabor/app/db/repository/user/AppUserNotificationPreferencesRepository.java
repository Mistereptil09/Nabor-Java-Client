// core-impl/src/main/java/tech/nabor/app/db/repository/user/AppUserNotificationPreferencesRepository.java
package tech.nabor.app.db.repository.user;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.user.UserNotificationPreferences;
import tech.nabor.api.repository.user.UserNotificationPreferencesRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppUserNotificationPreferencesRepository implements UserNotificationPreferencesRepository {

    private final Jdbi jdbi;

    public AppUserNotificationPreferencesRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class PreferencesMapper implements RowMapper<UserNotificationPreferences> {
        @Override
        public UserNotificationPreferences map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserNotificationPreferences(
                    rs.getString("user_id"),
                    rs.getInt("notif_new_follower") == 1,
                    rs.getInt("notif_new_listing")  == 1,
                    rs.getInt("notif_new_event")    == 1,
                    rs.getInt("notif_new_poll")     == 1,
                    rs.getInt("notif_waitlist")     == 1,
                    rs.getInt("notif_message")      == 1,
                    InstantMapper.fromNullableLong(rs, "updated_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<UserNotificationPreferences> findByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM user_notification_preferences WHERE user_id = :userId")
                        .bind("userId", userId)
                        .map(new PreferencesMapper())
                        .findOne()
        );
    }

    @Override
    public void save(UserNotificationPreferences prefs) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO user_notification_preferences (
                    user_id, notif_new_follower, notif_new_listing, notif_new_event,
                    notif_new_poll, notif_waitlist, notif_message, updated_at
                ) VALUES (
                    :userId, :notifNewFollower, :notifNewListing, :notifNewEvent,
                    :notifNewPoll, :notifWaitlist, :notifMessage, :updatedAt
                )
                ON CONFLICT(user_id) DO UPDATE SET
                    notif_new_follower = excluded.notif_new_follower,
                    notif_new_listing  = excluded.notif_new_listing,
                    notif_new_event    = excluded.notif_new_event,
                    notif_new_poll     = excluded.notif_new_poll,
                    notif_waitlist     = excluded.notif_waitlist,
                    notif_message      = excluded.notif_message,
                    updated_at         = excluded.updated_at
                """)
                        .bind("userId",           prefs.userId())
                        .bind("notifNewFollower", prefs.notifNewFollower() ? 1 : 0)
                        .bind("notifNewListing",  prefs.notifNewListing()  ? 1 : 0)
                        .bind("notifNewEvent",    prefs.notifNewEvent()    ? 1 : 0)
                        .bind("notifNewPoll",     prefs.notifNewPoll()     ? 1 : 0)
                        .bind("notifWaitlist",    prefs.notifWaitlist()    ? 1 : 0)
                        .bind("notifMessage",     prefs.notifMessage()     ? 1 : 0)
                        .bind("updatedAt",        InstantMapper.toLong(prefs.updatedAt()))
                        .execute()
        );
    }
}