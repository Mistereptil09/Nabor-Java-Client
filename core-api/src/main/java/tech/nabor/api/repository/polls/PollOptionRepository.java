package tech.nabor.api.repository.polls;

import tech.nabor.api.model.polls.PollOption;

import java.util.List;
import java.util.Optional;

public interface PollOptionRepository {
    List<PollOption> findByPollId(String pollId);
    Optional<PollOption> findById(String id);
    void save(PollOption option);
    void deleteByPollId(String pollId);                  // suppression en cascade
}