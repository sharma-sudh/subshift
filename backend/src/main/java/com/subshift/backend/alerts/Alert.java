package com.subshift.backend.alerts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.subshift.backend.subscriptions.Subscription;
import com.subshift.backend.pricelog.PriceLog;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_log_id", nullable = false)
    private PriceLog priceLog;

    @Column(name = "old_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal newPrice;

    // Alerts are normally created by the DB trigger (trg_flag_price_hike); the DB sets this default.
    @Column(name = "detected_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
}
