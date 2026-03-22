package tech.nabor.app.db.repository.listings;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.listings.ListingCategory;
import tech.nabor.api.repository.listings.ListingCategoryRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppListingCategoryRepository implements ListingCategoryRepository {

    private final Jdbi jdbi;

    public AppListingCategoryRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ListingCategoryMapper implements RowMapper<ListingCategory> {
        @Override
        public ListingCategory map(ResultSet rs, StatementContext ctx) throws SQLException {
            int parentId = rs.getInt("parent_category");
            Integer parentCategory = rs.wasNull() ? null : parentId;

            return new ListingCategory(
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
    public List<ListingCategory> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_category ORDER BY id ASC")
                        .map(new ListingCategoryMapper())
                        .list()
        );
    }

    @Override
    public Optional<ListingCategory> findById(int id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_category WHERE id = :id")
                        .bind("id", id)
                        .map(new ListingCategoryMapper())
                        .findOne()
        );
    }

    @Override
    public List<ListingCategory> findByParent(int parentId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_category WHERE parent_category = :parentId")
                        .bind("parentId", parentId)
                        .map(new ListingCategoryMapper())
                        .list()
        );
    }

    @Override
    public void save(ListingCategory category) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO listing_category (parent_category, category_name, created_at, updated_at)
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