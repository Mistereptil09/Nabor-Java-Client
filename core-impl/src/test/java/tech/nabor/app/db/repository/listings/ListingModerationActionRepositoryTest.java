package tech.nabor.app.db.repository.listings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.listings.Listing;
import tech.nabor.api.model.listings.ListingModerationAction;
import tech.nabor.api.model.enums.ListingStatus;
import tech.nabor.api.model.enums.ListingType;
import tech.nabor.api.model.enums.ModerationAction;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ListingModerationActionRepositoryTest extends BaseRepositoryTest {

    private AppListingModerationActionRepository repo;
    private AppUserRepository userRepo;
    private AppListingRepository listingRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppListingModerationActionRepository(jdbi);
        userRepo    = new AppUserRepository(jdbi);
        listingRepo = new AppListingRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.userWithRole("mod-1", "mod@test.com", UserRole.moderator));
        userRepo.save(UserFixtures.userWithRole("mod-2", "mod2@test.com", UserRole.moderator));

        listingRepo.save(new Listing("listing-1", "user-1", "T", null,
                null, ListingType.offer, 0, ListingStatus.open,
                null, null, Instant.now(), null, null, null));
        listingRepo.save(new Listing("listing-2", "user-1", "T", null,
                null, ListingType.offer, 0, ListingStatus.open,
                null, null, Instant.now(), null, null, null));
    }

    private ListingModerationAction action(String id, String listingId,
                                           String moderatorId, ModerationAction action) {
        return new ListingModerationAction(id, listingId, moderatorId,
                action, "Raison de test", Instant.now());
    }

    // ── findByListingId ───────────────────────────────────────────────────────

    @Test
    void findByListingId_returns_actions_for_listing() {
        repo.save(action("action-1", "listing-1", "mod-1", ModerationAction.warned));
        repo.save(action("action-2", "listing-1", "mod-1", ModerationAction.cancelled));
        repo.save(action("action-3", "listing-2", "mod-1", ModerationAction.warned));

        assertEquals(2, repo.findByListingId("listing-1").size());
    }

    @Test
    void findByListingId_returns_empty_when_no_actions() {
        assertTrue(repo.findByListingId("listing-1").isEmpty());
    }

    // ── findByModeratorId ─────────────────────────────────────────────────────

    @Test
    void findByModeratorId_returns_actions_for_moderator() {
        repo.save(action("action-1", "listing-1", "mod-1", ModerationAction.warned));
        repo.save(action("action-2", "listing-2", "mod-1", ModerationAction.cancelled));
        repo.save(action("action-3", "listing-1", "mod-2", ModerationAction.warned));

        assertEquals(2, repo.findByModeratorId("mod-1").size());
    }

    @Test
    void findByModeratorId_returns_empty_when_no_actions() {
        assertTrue(repo.findByModeratorId("mod-1").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_action() {
        repo.save(action("action-1", "listing-1", "mod-1", ModerationAction.warned));
        assertEquals(1, repo.findByListingId("listing-1").size());
    }

    @Test
    void save_persists_all_moderation_actions() {
        repo.save(action("action-1", "listing-1", "mod-1", ModerationAction.warned));
        repo.save(action("action-2", "listing-2", "mod-1", ModerationAction.cancelled));
        repo.save(action("action-3", "listing-1", "mod-2", ModerationAction.restored));

        assertEquals(ModerationAction.warned,    repo.findByListingId("listing-1").stream()
                .filter(a -> a.id().equals("action-1")).findFirst().orElseThrow().action());
        assertEquals(ModerationAction.cancelled, repo.findByListingId("listing-2").get(0).action());
        assertEquals(ModerationAction.restored,  repo.findByListingId("listing-1").stream()
                .filter(a -> a.id().equals("action-3")).findFirst().orElseThrow().action());
    }

    @Test
    void save_persists_reason() {
        repo.save(new ListingModerationAction("action-1", "listing-1", "mod-1",
                ModerationAction.warned, "Contenu offensant", Instant.now()));

        ListingModerationAction found = repo.findByListingId("listing-1").get(0);
        assertEquals("Contenu offensant", found.reason());
    }
}