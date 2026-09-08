package poneytoponey;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; //D2

public class Chat implements Serializable {

    private static final long serialVersionUID = 1L; // D2 pour l'implémentation de serializable

    private UUID uuid;
    private boolean approved;
    private List<Message> messages;
    private String creator; // the username of the creator
    private String otherUsername; // the username of the remote client
    // D1
    private transient Map<UUID, Instant> pendingAcks = new ConcurrentHashMap<>(); // suivi des ack en attente
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(5);

    // When creating a Chat locally
    public Chat(String otherUsername, String creatorUsername) {
        this(otherUsername, UUID.randomUUID(), creatorUsername);
    }

    // When receiving a chat ID from a remote client
    public Chat(String otherUsername, UUID existingChatID, String creatorUsername) {
        this.uuid = existingChatID;
        this.approved = false;
        this.messages = new ArrayList<>();
        this.otherUsername = otherUsername;
        this.creator = creatorUsername;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public String getOtherUsername() {
        return this.otherUsername;
    }

    public String getCreator() {
        return this.creator;
    }

    public boolean getApproved() {
        return this.approved;
    }

    public void setApproved(boolean t) {
        this.approved = t;
    }

    public void insertNewReceivedMessage(Message msg) {
        messages.add(msg);
    }

    public Message insertNewMessage(String text, String author, boolean important) {
        long ts = System.currentTimeMillis();
        Message m = new Message(text, ts, author, important);
        messages.add(m);
        return m;
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    // D1
    public void resetAcks() {
        pendingAcks = new ConcurrentHashMap<>();
    }

    // D1
    public void registerPendingAck(UUID messageId) {
        pendingAcks.put(messageId, Instant.now());
    }

    // D1
    public void receiveAck(UUID messageId) {
        pendingAcks.remove(messageId);
    }

    // D1
    public boolean hasTimedOutAck() {
        Instant now = Instant.now();
        return pendingAcks.values().stream()
                .anyMatch(sent -> Duration.between(sent, now).compareTo(ACK_TIMEOUT) > 0);
    }

}
