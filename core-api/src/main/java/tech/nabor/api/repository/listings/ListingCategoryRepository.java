package tech.nabor.api.repository.listings;

import tech.nabor.api.model.listings.ListingCategory;

import java.util.List;
import java.util.Optional;

public interface ListingCategoryRepository {
    List<ListingCategory> findAll();
    Optional<ListingCategory> findById(int id);
    List<ListingCategory> findByParent(int parentId);    // sub-categories
    void save(ListingCategory category);
}