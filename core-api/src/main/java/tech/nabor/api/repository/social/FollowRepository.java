package tech.nabor.api.repository.social;

import tech.nabor.api.model.social.Follow;

import java.util.List;

public interface FollowRepository {
    List<Follow> findFollowersByUserId(String userId);   // who follows this user
    List<Follow> findFollowingByUserId(String userId);   // who this user follows
    boolean isFollowing(String followerId, String followedId);
    void save(Follow follow);
    void delete(String followerId, String followedId);
}