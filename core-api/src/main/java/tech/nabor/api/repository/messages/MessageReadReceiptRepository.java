package tech.nabor.api.repository.messages;

import tech.nabor.api.model.messages.MessageReadReceipt;

import java.util.List;

public interface MessageReadReceiptRepository {
    List<MessageReadReceipt> findByMessageId(String messageId);
    boolean hasRead(String userId, String messageId);
    void save(MessageReadReceipt receipt);
}