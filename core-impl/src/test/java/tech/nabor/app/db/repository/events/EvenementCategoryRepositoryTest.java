package tech.nabor.app.db.repository.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.events.EvenementCategory;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EvenementCategoryRepositoryTest extends BaseRepositoryTest {

    private AppEvenementCategoryRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppEvenementCategoryRepository(jdbi);
    }

    private EvenementCategory category(Integer parentId, String name) {
        return new EvenementCategory(0, parentId, name, Instant.now(), null);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returns_empty_when_no_categories() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_returns_all_categories() {
        repo.save(category(null, "Sports"));
        repo.save(category(null, "Culture"));

        assertEquals(2, repo.findAll().size());
    }

    @Test
    void findAll_ordered_by_id() {
        repo.save(category(null, "Sports"));
        repo.save(category(null, "Culture"));

        List<EvenementCategory> all = repo.findAll();
        assertTrue(all.get(0).id() < all.get(1).id());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_category_when_exists() {
        repo.save(category(null, "Sports"));
        int id = repo.findAll().getFirst().id();

        Optional<EvenementCategory> found = repo.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Sports", found.get().categoryName());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById(999).isEmpty());
    }

    // ── findByParent ──────────────────────────────────────────────────────────

    @Test
    void findByParent_returns_subcategories() {
        repo.save(category(null, "Sports"));
        int parentId = repo.findAll().getFirst().id();

        repo.save(category(parentId, "Football"));
        repo.save(category(parentId, "Tennis"));

        assertEquals(2, repo.findByParent(parentId).size());
    }

    @Test
    void findByParent_returns_empty_when_no_children() {
        assertTrue(repo.findByParent(999).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_category() {
        repo.save(category(null, "Sports"));
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void save_persists_null_parent() {
        repo.save(category(null, "Sports"));
        int id = repo.findAll().getFirst().id();

        assertNull(repo.findById(id).orElseThrow().parentCategory());
    }

    @Test
    void save_persists_parent_id() {
        repo.save(category(null, "Sports"));
        int parentId = repo.findAll().getFirst().id();
        repo.save(category(parentId, "Football"));

        EvenementCategory sub = repo.findByParent(parentId).getFirst();
        assertEquals(parentId, sub.parentCategory());
    }
}