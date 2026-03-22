package tech.nabor.api.repository.messages;

import tech.nabor.api.model.enums.ChatGroupType;
import tech.nabor.api.model.messages.ChatGroup;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository {
    Optional<ChatGroup> findById(String id);
    List<ChatGroup> findByType(ChatGroupType type);
    void save(ChatGroup group);
    void delete(String id);                              // soft delete — changes deleted_at
}