package com.example.leaveplatform.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The one row that says whether the platform sends mail and where it sends it.
 *
 * <p>Everything is nullable except the flags, because a university that has not
 * been given its mail server details yet still has to be able to open the
 * screen and see what is missing. {@code enabled} is a separate switch from
 * "a host has been typed": the person who fills in the settings decides when the
 * platform starts speaking to the mail server, not the moment they finish typing.
 */
@Entity
@Table(name = "mail_setting")
public class MailSettings {

    /** The only row the table holds. */
    public static final long ROW = 1L;

    @Id
    private Long id = ROW;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(length = 250)
    private String host;

    private Integer port;

    @Column(length = 250)
    private String username;

    @Column(name = "password_cipher", length = 1000)
    private String passwordCipher;

    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "use_tls", nullable = false)
    private Boolean useTls = true;

    @Column(name = "mailed_events", length = 400)
    private String mailedEvents;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MailSettings() {
    }

    MailSettings(String host, Integer port, String username, String fromAddress, boolean useTls,
            boolean enabled, Set<String> mailedEvents) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.fromAddress = fromAddress;
        this.useTls = useTls;
        this.enabled = enabled;
        this.mailedEvents = join(mailedEvents);
    }

    /**
     * Whether a message of this kind should also go out as mail. An empty list
     * means every kind, because a person who turns mail on and names nothing
     * almost certainly means "send what the bell shows" - and a list that has to
     * be filled in before anything works is a switch that is off.
     */
    boolean mails(String kind) {
        if (!Boolean.TRUE.equals(enabled)) {
            return false;
        }
        Set<String> listed = events();
        return listed.isEmpty() || listed.contains(kind);
    }

    Set<String> events() {
        Set<String> kinds = new LinkedHashSet<>();
        if (mailedEvents != null) {
            for (String part : mailedEvents.split(",")) {
                if (!part.isBlank()) {
                    kinds.add(part.trim());
                }
            }
        }
        return kinds;
    }

    private static String join(Set<String> kinds) {
        StringBuilder words = new StringBuilder();
        for (String kind : kinds) {
            if (!words.isEmpty()) {
                words.append(',');
            }
            words.append(kind);
        }
        return words.toString();
    }

    public Long getId() {
        return id;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isUseTls() {
        return Boolean.TRUE.equals(useTls);
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    String getPasswordCipher() {
        return passwordCipher;
    }

    void setPasswordCipher(String passwordCipher) {
        this.passwordCipher = passwordCipher;
    }

    void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    void setHost(String host) {
        this.host = host;
    }

    void setPort(Integer port) {
        this.port = port;
    }

    void setUsername(String username) {
        this.username = username;
    }

    void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    void setUseTls(Boolean useTls) {
        this.useTls = useTls;
    }

    void setMailedEvents(Set<String> mailedEvents) {
        this.mailedEvents = join(mailedEvents);
    }

    void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
