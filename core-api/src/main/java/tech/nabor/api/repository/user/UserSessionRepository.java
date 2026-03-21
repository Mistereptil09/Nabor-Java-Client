package tech.nabor.api.repository.user;

import tech.nabor.api.model.user.UserSession;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository {
    Optional<UserSession> findById(String id);
    Optional<UserSession> findByTokenHash(String hash);
    List<UserSession> findActiveByUserId(String userId); // revoked_at IS NULL
    void save(UserSession session);
    void revoke(String id);                              // changes revoked_at = now
    void revokeAllForUser(String userId);                // revokes connections
}