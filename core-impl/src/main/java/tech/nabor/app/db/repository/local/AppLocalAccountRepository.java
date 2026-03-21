package tech.nabor.app.db.repository.local;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.api.repository.local.LocalAccountRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppLocalAccountRepository implements LocalAccountRepository {

    private final Jdbi jdbi;

    public AppLocalAccountRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class LocalAccountMapper implements RowMapper<LocalAccount> {
        @Override
        public LocalAccount map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new LocalAccount(
                    rs.getString("user_id"),
                    rs.getString("email"),
                    rs.getString("display_name"),
                    rs.getInt("is_active") == 1,
                    InstantMapper.fromNullableLong(rs, "last_login_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<LocalAccount> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM local_accounts ORDER BY last_login_at DESC")
                        .map(new LocalAccountMapper())
                        .list()
        );
    }

    @Override
    public Optional<LocalAccount> findById(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM local_accounts WHERE user_id = :userId")
                        .bind("userId", userId)
                        .map(new LocalAccountMapper())
                        .findOne()
        );
    }

    @Override
    public Optional<LocalAccount> findActive() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM local_accounts WHERE is_active = 1")
                        .map(new LocalAccountMapper())
                        .findOne()
        );
    }

    @Override
    public void save(LocalAccount account) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO local_accounts (user_id, email, display_name, is_active, last_login_at)
                VALUES (:userId, :email, :displayName, :isActive, :lastLoginAt)
                ON CONFLICT(user_id) DO UPDATE SET
                    email         = excluded.email,
                    display_name  = excluded.display_name,
                    is_active     = excluded.is_active,
                    last_login_at = excluded.last_login_at
                """)
                        .bind("userId",      account.userId())
                        .bind("email",       account.email())
                        .bind("displayName", account.displayName())
                        .bind("isActive",    account.isActive() ? 1 : 0)
                        .bind("lastLoginAt", InstantMapper.toLong(account.lastLoginAt()))
                        .execute()
        );
    }

    @Override
    public void setActive(String userId) {
        jdbi.useTransaction(h -> {
            // désactive tous les comptes
            h.createUpdate("UPDATE local_accounts SET is_active = 0")
                    .execute();
            // active uniquement celui demandé
            h.createUpdate("UPDATE local_accounts SET is_active = 1 WHERE user_id = :userId")
                    .bind("userId", userId)
                    .execute();
        });
    }

    @Override
    public void delete(String userId) {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM local_accounts WHERE user_id = :userId")
                        .bind("userId", userId)
                        .execute()
        );
    }
}