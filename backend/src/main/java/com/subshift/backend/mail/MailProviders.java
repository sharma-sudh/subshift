package com.subshift.backend.mail;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Looks up the provider for a connection's type. */
@Component
public class MailProviders {

    private final Map<ProviderType, MailProvider> byType = new EnumMap<>(ProviderType.class);

    public MailProviders(List<MailProvider> providers) {
        for (MailProvider provider : providers) {
            byType.put(provider.type(), provider);
        }
    }

    public MailSession open(Integer userId, ProviderType type) {
        MailProvider provider = byType.get(type);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, type.label() + " is not supported");
        }
        return provider.open(userId);
    }
}
