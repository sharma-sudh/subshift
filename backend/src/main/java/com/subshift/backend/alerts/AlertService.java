package com.subshift.backend.alerts;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final CurrentUserService currentUserService;

    public record AlertResponse(Integer id, String serviceName, BigDecimal oldPrice, BigDecimal newPrice,
                                LocalDateTime detectedAt, boolean isRead) {
        static AlertResponse from(Alert a) {
            return new AlertResponse(a.getId(), a.getSubscription().getService().getName(),
                    a.getOldPrice(), a.getNewPrice(), a.getDetectedAt(), Boolean.TRUE.equals(a.getIsRead()));
        }
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(String email) {
        User user = currentUserService.require(email);
        return alertRepository.findBySubscriptionUserIdOrderByDetectedAtDesc(user.getId())
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse markRead(String email, Integer id) {
        User user = currentUserService.require(email);
        // Someone else's alert looks exactly like a missing one: 404, not 403.
        Alert alert = alertRepository.findByIdAndSubscriptionUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        alert.setIsRead(true);
        return AlertResponse.from(alert);
    }

    /** Marks all of the user's unread alerts as read with one stored-procedure call. Returns how many were unread. */
    @Transactional
    public int markAllRead(String email) {
        User user = currentUserService.require(email);
        long unread = alertRepository.countUnread(user.getId());
        if (unread > 0) {
            alertRepository.markAllReadViaProcedure(user.getId());
        }
        return (int) unread;
    }
}
