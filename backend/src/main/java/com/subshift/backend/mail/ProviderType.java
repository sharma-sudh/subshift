package com.subshift.backend.mail;

/** The mail services a user can connect. Stored by name in gmail_connections.provider. */
public enum ProviderType {
    GMAIL("Gmail"),
    OUTLOOK("Outlook"),
    YAHOO("Yahoo Mail");

    private final String label;

    ProviderType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
