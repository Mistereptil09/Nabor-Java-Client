package tech.nabor.api.repository.social;

import tech.nabor.api.model.social.UserBlock;

import java.util.List;

public interface UserBlockRepository {
    List<UserBlock> findByBlockerId(String blockerId);   // qui cet user a bloqué
    boolean isBlocked(String blockerId, String blockedId);
    void save(UserBlock block);
    void delete(String blockerId, String blockedId);
}