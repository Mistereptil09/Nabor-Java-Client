package tech.nabor.api.repository.social;

import tech.nabor.api.model.social.Friendship;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository {
    List<Friendship> findByUserId(String userId);        // all actives friendships
    Optional<Friendship> findByPair(String user1Id, String user2Id);
    boolean areFriends(String user1Id, String user2Id);
    void save(Friendship friendship);
    void delete(String id);                              // unfriended_at
}