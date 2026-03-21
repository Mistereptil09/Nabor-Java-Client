package tech.nabor.api.repository.polls;

import tech.nabor.api.model.polls.Vote;

import java.util.List;
import java.util.Optional;

public interface VoteRepository {
    List<Vote> findByOptionId(String optionId);
    List<Vote> findByUserAndPoll(String userId, String pollId); // votes d'un user sur un sondage
    Optional<Vote> findByUserAndOption(String userId, String optionId);
    int countByOptionId(String optionId);                // total votes sur une option
    void save(Vote vote);                                // insert ou update (vote modifiable)
    void deleteByUserAndPoll(String userId, String pollId); // retrait du vote
}