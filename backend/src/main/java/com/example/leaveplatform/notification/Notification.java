package com.example.leaveplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One in-app message for one account, written at the moment something happens
 * to someone else's leave request and read from the bell in the header. The
 * words are fixed when the row is created - only {@code read_at} ever changes -
 * so a message cannot be quietly rewritten after the fact.
 */
@Entity
@Table(name = "notification")
public class Notification {

    static final int MAX_TITLE = 200;
    static final int MAX_BODY = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_username", nullable = false, updatable = false, length = 60)
    private String recipientUsername;

    @Column(nullable = false, updatable = false, length = 40)
    private String kind;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(updatable = false, length = 1000)
    private String body;

    @Column(name = "application_id", updatable = false)
    private Long applicationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(String recipientUsername, NotificationKind kind, String title, String body,
            Long applicationId) {
        this.recipientUsername = recipientUsername;
        this.kind = kind.name();
        this.title = title;
        this.body = body;
        this.applicationId = applicationId;
        this.createdAt = Instant.now();
    }

    /** Records that the recipient has seen it. A second read changes nothing. */
    void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public String getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isUnread() {
        return readAt == null;
    }
}
