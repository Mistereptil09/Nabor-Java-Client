package tech.nabor.api.repository.messages;

import tech.nabor.api.model.messages.UserInGroup;

import java.util.List;
import java.util.Optional;

public interface UserInGroupRepository {
    List<UserInGroup> findByGroupId(String groupId);     // group members
    List<UserInGroup> findByUserId(String userId);       // groups of a user
    Optional<UserInGroup> findByUserAndGroup(String userId, String groupId);
    boolean isMember(String userId, String groupId);
    void save(UserInGroup userInGroup);
    void leave(String userId, String groupId);           // changes left_at
    void kick(String userId, String groupId);            // changes kicked_at
}