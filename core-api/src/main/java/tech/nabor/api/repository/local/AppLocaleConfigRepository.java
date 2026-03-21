package tech.nabor.api.repository.local;

import tech.nabor.api.model.local.AppLocaleConfig;

import java.util.Optional;

public interface AppLocaleConfigRepository {
    Optional<AppLocaleConfig> findByUserId(String userId);
    void save(AppLocaleConfig config);               // insert or update
}