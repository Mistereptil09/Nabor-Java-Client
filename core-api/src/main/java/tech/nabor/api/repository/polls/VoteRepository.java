package tech.nabor.api.repository.polls;

import tech.nabor.api.model.polls.Vote;

import java.util.List;
import java.util.Optional;

public interface VoteRepository {
    List<Vote> findByOptionId(String optionId);
    List<Vote> findByUserAndPoll(String userId, String pollId); // users votes on a poll
    Optional<Vote> findByUserAndOption(String userId, String optionId);
    int countByOptionId(String optionId);                // total votes on an option
    void save(Vote vote);                                // insert or update (modifiable vote)
    void deleteByUserAndPoll(String userId, String pollId); // remove a vote
}