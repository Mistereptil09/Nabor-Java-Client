package tech.nabor.api.repository.events;

import tech.nabor.api.model.events.EvenementCategory;

import java.util.List;
import java.util.Optional;

public interface EvenementCategoryRepository {
    List<EvenementCategory> findAll();
    Optional<EvenementCategory> findById(int id);
    List<EvenementCategory> findByParent(int parentId);
    void save(EvenementCategory category);
}