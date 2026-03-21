package tech.nabor.api.repository.user;

import tech.nabor.api.model.user.UserNotificationPreferences;

import java.util.Optional;

public interface UserNotificationPreferencesRepository {
    Optional<UserNotificationPreferences> findByUserId(String userId);
    void save(UserNotificationPreferences prefs);
}