package tech.nabor.app.db.repository.listings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.listings.ListingCategory;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ListingCategoryRepositoryTest extends BaseRepositoryTest {

    private AppListingCategoryRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppListingCategoryRepository(jdbi);
    }

    private ListingCategory category(Integer parentId, String name) {
        return new ListingCategory(0, parentId, name, Instant.now(), null);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returns_empty_when_no_categories() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_returns_all_categories() {
        repo.save(category(null, "Services"));
        repo.save(category(null, "Objets"));

        assertEquals(2, repo.findAll().size());
    }

    @Test
    void findAll_ordered_by_id() {
        repo.save(category(null, "Services"));
        repo.save(category(null, "Objets"));

        List<ListingCategory> all = repo.findAll();
        assertTrue(all.get(0).id() < all.get(1).id());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_category_when_exists() {
        repo.save(category(null, "Services"));
        int id = repo.findAll().get(0).id();

        Optional<ListingCategory> found = repo.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Services", found.get().categoryName());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById(999).isEmpty());
    }

    // ── findByParent ──────────────────────────────────────────────────────────

    @Test
    void findByParent_returns_subcategories() {
        repo.save(category(null, "Services"));
        int parentId = repo.findAll().get(0).id();

        repo.save(category(parentId, "Jardinage"));
        repo.save(category(parentId, "Bricolage"));

        List<ListingCategory> subs = repo.findByParent(parentId);
        assertEquals(2, subs.size());
    }

    @Test
    void findByParent_does_not_return_root_categories() {
        repo.save(category(null, "Services"));
        repo.save(category(null, "Objets"));
        int parentId = repo.findAll().get(0).id();

        assertTrue(repo.findByParent(parentId).isEmpty());
    }

    @Test
    void findByParent_returns_empty_when_no_children() {
        assertTrue(repo.findByParent(999).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_category() {
        repo.save(category(null, "Services"));
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void save_persists_null_parent() {
        repo.save(category(null, "Services"));
        int id = repo.findAll().getFirst().id();

        assertNull(repo.findById(id).orElseThrow().parentCategory());
    }

    @Test
    void save_persists_parent_id() {
        repo.save(category(null, "Services"));
        int parentId = repo.findAll().get(0).id();
        repo.save(category(parentId, "Jardinage"));

        List<ListingCategory> subs = repo.findByParent(parentId);
        assertEquals(parentId, subs.get(0).parentCategory());
    }
}