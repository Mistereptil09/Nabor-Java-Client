package tech.nabor.app.db.repository.events;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.events.EvenementCategory;
import tech.nabor.api.repository.events.EvenementCategoryRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppEvenementCategoryRepository implements EvenementCategoryRepository {

    private final Jdbi jdbi;

    public AppEvenementCategoryRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class EvenementCategoryMapper implements RowMapper<EvenementCategory> {
        @Override
        public EvenementCategory map(ResultSet rs, StatementContext ctx) throws SQLException {
            int parentId = rs.getInt("parent_category");
            Integer parentCategory = rs.wasNull() ? null : parentId;
            return new EvenementCategory(
                    rs.getInt("id"),
                    parentCategory,
                    rs.getString("category_name"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<EvenementCategory> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM evenements_category ORDER BY id ASC")
                        .map(new EvenementCategoryMapper())
                        .list()
        );
    }

    @Override
    public Optional<EvenementCategory> findById(int id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM evenements_category WHERE id = :id")
                        .bind("id", id)
                        .map(new EvenementCategoryMapper())
                        .findOne()
        );
    }

    @Override
    public List<EvenementCategory> findByParent(int parentId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM evenements_category WHERE parent_category = :parentId")
                        .bind("parentId", parentId)
                        .map(new EvenementCategoryMapper())
                        .list()
        );
    }

    @Override
    public void save(EvenementCategory category) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO evenements_category (parent_category, category_name, created_at, updated_at)
                VALUES (:parentCategory, :categoryName, :createdAt, :updatedAt)
                ON CONFLICT(id) DO UPDATE SET
                    parent_category = excluded.parent_category,
                    category_name   = excluded.category_name,
                    updated_at      = excluded.updated_at
                """)
                        .bind("parentCategory", category.parentCategory())
                        .bind("categoryName",   category.categoryName())
                        .bind("createdAt",      InstantMapper.toLong(category.createdAt()))
                        .bind("updatedAt",      InstantMapper.toLong(category.updatedAt()))
                        .execute()
        );
    }
}