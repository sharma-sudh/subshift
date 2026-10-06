package com.subshift.backend.training;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import com.subshift.backend.users.User;

@Entity
@Table(name = "classifier_training_emails")
@Getter
@Setter
@NoArgsConstructor
public class ClassifierTrainingEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "email_text", nullable = false, columnDefinition = "TEXT")
    private String emailText;

    @Column(nullable = false, length = 20)
    private String label; // 'RENEWAL' or 'NOT_RENEWAL' (DB CHECK constraint)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "labeled_by", nullable = false)
    private User labeledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
