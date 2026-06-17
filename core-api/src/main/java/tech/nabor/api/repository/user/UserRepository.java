package tech.nabor.api.repository.user;

import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.user.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    List<User> findAll();
    Optional<User> findById(String id);
    Optional<User> findByEmail(String email);
    List<User> findByNeighbourhood(String neighbourhoodId);
    List<User> findByRole(UserRole role);
    void save(User user);
    void delete(String id);                          // soft delete — changes deleted_at
}