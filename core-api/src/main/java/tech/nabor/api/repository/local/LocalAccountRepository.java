package tech.nabor.api.repository.local;

import tech.nabor.api.model.local.LocalAccount;

import java.util.List;
import java.util.Optional;

public interface LocalAccountRepository {
    List<LocalAccount> findAll();                    // all known accounts
    Optional<LocalAccount> findById(String userId);
    Optional<LocalAccount> findActive();             // currently connected account
    void save(LocalAccount account);                 // insert or update
    void setActive(String userId);                   // deactivate all, activate only id
    void delete(String userId);
}