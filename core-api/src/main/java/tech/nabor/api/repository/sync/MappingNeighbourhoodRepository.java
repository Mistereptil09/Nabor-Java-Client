package tech.nabor.api.repository.sync;

import tech.nabor.api.model.sync.MappingNeighbourhood;

import java.util.List;

public interface MappingNeighbourhoodRepository {
    List<MappingNeighbourhood> findAll();
    void upsert(String neighbourhoodId, String neighbourhoodName);
}