// core-impl/src/main/java/tech/nabor/app/db/repository/user/AppUserRepository.java
package tech.nabor.app.db.repository.user;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;
import tech.nabor.api.model.user.User;
import tech.nabor.api.repository.user.UserRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppUserRepository implements UserRepository {

    private final Jdbi jdbi;

    public AppUserRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class UserMapper implements RowMapper<User> {
        @Override
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new User(
                    rs.getString("id"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    rs.getString("totp_secret"),
                    rs.getString("stripe_account_id"),
                    rs.getString("neighbourhood_id"),
                    Visibility.fromSqlValue(rs.getString("visibility")),
                    rs.getString("bio"),
                    MessagePolicy.valueOf(rs.getString("message_policy")),
                    rs.getString("locale"),
                    rs.getString("profile_picture_mongo_id"),
                    rs.getString("banner_mongo_id"),
                    UserRole.valueOf(rs.getString("role")),
                    InstantMapper.fromNullableLong(rs, "last_login_at"),
                    InstantMapper.fromNullableLong(rs, "password_changed_at"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at"),
                    InstantMapper.fromNullableLong(rs, "deleted_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<User> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM users WHERE deleted_at IS NULL ORDER BY last_name")
                        .map(new UserMapper())
                        .list()
        );
    }

    @Override
    public Optional<User> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM users WHERE id = :id")
                        .bind("id", id)
                        .map(new UserMapper())
                        .findOne()
        );
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM users WHERE email = :email AND deleted_at IS NULL")
                        .bind("email", email)
                        .map(new UserMapper())
                        .findOne()
        );
    }

    @Override
    public List<User> findByNeighbourhood(String neighbourhoodId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM users
                WHERE neighbourhood_id = :neighbourhoodId AND deleted_at IS NULL
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .map(new UserMapper())
                        .list()
        );
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM users
                WHERE role = :role AND deleted_at IS NULL
                """)
                        .bind("role", role.name())
                        .map(new UserMapper())
                        .list()
        );
    }

    @Override
    public void save(User user) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO users (
                    id, first_name, last_name, email, password_hash, totp_secret,
                    stripe_account_id, neighbourhood_id, visibility, bio, message_policy,
                    locale, profile_picture_mongo_id, banner_mongo_id, role,
                    last_login_at, password_changed_at, created_at, updated_at, deleted_at
                ) VALUES (
                    :id, :firstName, :lastName, :email, :passwordHash, :totpSecret,
                    :stripeAccountId, :neighbourhoodId, :visibility, :bio, :messagePolicy,
                    :locale, :profilePictureMongoId, :bannerMongoId, :role,
                    :lastLoginAt, :passwordChangedAt, :createdAt, :updatedAt, :deletedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    first_name               = excluded.first_name,
                    last_name                = excluded.last_name,
                    email                    = excluded.email,
                    password_hash            = excluded.password_hash,
                    totp_secret              = excluded.totp_secret,
                    stripe_account_id        = excluded.stripe_account_id,
                    neighbourhood_id         = excluded.neighbourhood_id,
                    visibility               = excluded.visibility,
                    bio                      = excluded.bio,
                    message_policy           = excluded.message_policy,
                    locale                   = excluded.locale,
                    profile_picture_mongo_id = excluded.profile_picture_mongo_id,
                    banner_mongo_id          = excluded.banner_mongo_id,
                    role                     = excluded.role,
                    last_login_at            = excluded.last_login_at,
                    password_changed_at      = excluded.password_changed_at,
                    updated_at               = excluded.updated_at,
                    deleted_at               = excluded.deleted_at
                """)
                        .bind("id",                    user.id())
                        .bind("firstName",             user.firstName())
                        .bind("lastName",              user.lastName())
                        .bind("email",                 user.email())
                        .bind("passwordHash",          user.passwordHash())
                        .bind("totpSecret",            user.totpSecret())
                        .bind("stripeAccountId",       user.stripeAccountId())
                        .bind("neighbourhoodId",       user.neighbourhoodId())
                        .bind("visibility",            user.visibility().toSqlValue())
                        .bind("bio",                   user.bio())
                        .bind("messagePolicy",         user.messagePolicy().name())
                        .bind("locale",                user.locale())
                        .bind("profilePictureMongoId", user.profilePictureMongoId())
                        .bind("bannerMongoId",         user.bannerMongoId())
                        .bind("role",                  user.role().name())
                        .bind("lastLoginAt",           InstantMapper.toLong(user.lastLoginAt()))
                        .bind("passwordChangedAt",     InstantMapper.toLong(user.passwordChangedAt()))
                        .bind("createdAt",             InstantMapper.toLong(user.createdAt()))
                        .bind("updatedAt",             InstantMapper.toLong(user.updatedAt()))
                        .bind("deletedAt",             InstantMapper.toLong(user.deletedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE users SET deleted_at = :now WHERE id = :id
                """)
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}