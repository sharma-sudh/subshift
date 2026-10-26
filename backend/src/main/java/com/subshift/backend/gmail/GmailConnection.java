package com.subshift.backend.gmail;

import com.subshift.backend.mail.ProviderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import com.subshift.backend.users.User;

/**
 * A user's connected mailbox. The class and table keep their original names, but a row can now be a Gmail,
 * Outlook or Yahoo connection (see provider). Gmail and Outlook store OAuth tokens; Yahoo stores an IMAP app
 * password. Every secret is encrypted with TokenEncryptor.
 */
@Entity
@Table(name = "gmail_connections")
@Getter
@Setter
@NoArgsConstructor
public class GmailConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProviderType provider = ProviderType.GMAIL;

    /** The address that was connected, when we know it (Yahoo). */
    @Column(name = "mailbox_address", length = 255)
    private String mailboxAddress;

    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(name = "app_password_encrypted", columnDefinition = "TEXT")
    private String appPasswordEncrypted;

    @CreationTimestamp
    @Column(name = "connected_at", nullable = false, updatable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /**
     * Switches this row to another provider. Everything that belonged to the old mailbox is cleared, including
     * the sync watermark, so the new mailbox is read from the start instead of from the old mailbox's position.
     */
    public void useProvider(ProviderType type) {
        if (this.provider != type) {
            this.provider = type;
            this.mailboxAddress = null;
            this.accessTokenEncrypted = null;
            this.refreshTokenEncrypted = null;
            this.appPasswordEncrypted = null;
            this.lastSyncedAt = null;
        }
    }
}
