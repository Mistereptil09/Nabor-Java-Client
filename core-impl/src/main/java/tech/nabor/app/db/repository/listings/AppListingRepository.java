package tech.nabor.app.db.repository.listings;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.listings.Listing;
import tech.nabor.api.model.enums.ListingStatus;
import tech.nabor.api.model.enums.ListingType;
import tech.nabor.api.repository.listings.ListingRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppListingRepository implements ListingRepository {

    private final Jdbi jdbi;

    public AppListingRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ListingMapper implements RowMapper<Listing> {
        @Override
        public Listing map(ResultSet rs, StatementContext ctx) throws SQLException {
            int categoryId = rs.getInt("category_id");
            return new Listing(
                    rs.getString("id"),
                    rs.getString("creator_id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.wasNull() ? null : categoryId,
                    ListingType.valueOf(rs.getString("listing_type")),
                    rs.getInt("price_cents"),
                    ListingStatus.valueOf(rs.getString("status")),
                    rs.getString("neighbourhood_id"),
                    rs.getString("mongo_document_id"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at"),
                    InstantMapper.fromNullableLong(rs, "closed_at"),
                    InstantMapper.fromNullableLong(rs, "deleted_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<Listing> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listings WHERE id = :id")
                        .bind("id", id)
                        .map(new ListingMapper())
                        .findOne()
        );
    }

    @Override
    public List<Listing> findByCreatorId(String creatorId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listings
                WHERE creator_id = :creatorId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """)
                        .bind("creatorId", creatorId)
                        .map(new ListingMapper())
                        .list()
        );
    }

    @Override
    public List<Listing> findByNeighbourhood(String neighbourhoodId, ListingStatus status, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listings
                WHERE neighbourhood_id = :neighbourhoodId
                AND status = :status
                AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("status",          status.name())
                        .bind("limit",           limit)
                        .map(new ListingMapper())
                        .list()
        );
    }

    @Override
    public List<Listing> findByStatus(ListingStatus status, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listings
                WHERE status = :status AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("status", status.name())
                        .bind("limit",  limit)
                        .map(new ListingMapper())
                        .list()
        );
    }

    @Override
    public void save(Listing listing) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO listings (
                    id, creator_id, title, description, category_id, listing_type,
                    price_cents, status, neighbourhood_id, mongo_document_id,
                    created_at, updated_at, closed_at, deleted_at
                ) VALUES (
                    :id, :creatorId, :title, :description, :categoryId, :listingType,
                    :priceCents, :status, :neighbourhoodId, :mongoDocumentId,
                    :createdAt, :updatedAt, :closedAt, :deletedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    title             = excluded.title,
                    description       = excluded.description,
                    category_id       = excluded.category_id,
                    listing_type      = excluded.listing_type,
                    price_cents       = excluded.price_cents,
                    status            = excluded.status,
                    neighbourhood_id  = excluded.neighbourhood_id,
                    mongo_document_id = excluded.mongo_document_id,
                    updated_at        = excluded.updated_at,
                    closed_at         = excluded.closed_at,
                    deleted_at        = excluded.deleted_at
                """)
                        .bind("id",               listing.id())
                        .bind("creatorId",        listing.creatorId())
                        .bind("title",            listing.title())
                        .bind("description",      listing.description())
                        .bind("categoryId",       listing.categoryId())
                        .bind("listingType",      listing.listingType().name())
                        .bind("priceCents",       listing.priceCents())
                        .bind("status",           listing.status().name())
                        .bind("neighbourhoodId",  listing.neighbourhoodId())
                        .bind("mongoDocumentId",  listing.mongoDocumentId())
                        .bind("createdAt",        InstantMapper.toLong(listing.createdAt()))
                        .bind("updatedAt",        InstantMapper.toLong(listing.updatedAt()))
                        .bind("closedAt",         InstantMapper.toLong(listing.closedAt()))
                        .bind("deletedAt",        InstantMapper.toLong(listing.deletedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE listings SET deleted_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}