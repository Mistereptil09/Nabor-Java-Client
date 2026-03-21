package tech.nabor.api.repository.messages;

import tech.nabor.api.model.messages.MessageMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageMetadataRepository {
    Optional<MessageMetadata> findById(String id);
    List<MessageMetadata> findByGroupId(String groupId, int limit);           // messages récents
    List<MessageMetadata> findByGroupIdBefore(String groupId, Instant before, int limit); // pagination
    List<MessageMetadata> findThreadByParentId(String parentMessageId);       // réponses
    void save(MessageMetadata message);
    void markDeleted(String id);                         // met is_deleted + deleted_at
}