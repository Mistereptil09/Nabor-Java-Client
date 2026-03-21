package tech.nabor.api.repository.polls;

import tech.nabor.api.model.polls.Poll;

import java.util.List;
import java.util.Optional;

public interface PollRepository {
    Optional<Poll> findById(String id);
    List<Poll> findByCreatorId(String creatorId);
    List<Poll> findByNeighbourhood(String neighbourhoodId, int limit);
    List<Poll> findActive(String neighbourhoodId, int limit); // closed_at IS NULL, ends_at > now
    void save(Poll poll);
    void close(String id, String closedBy);              // met closed_at + closed_by
    void delete(String id);                              // soft delete — met deleted_at
}