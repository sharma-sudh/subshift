package com.subshift.backend.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.subshift.backend.common.RestClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads candidate emails from the user's Gmail through the Gmail REST API (read-only scope). */
@Slf4j
@Component
public class EmailFetcher {

    private static final String API = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    public record FetchedEmail(String id, String subject, String from, LocalDate date, String body) {}

    private final EmailProperties props;
    private final RestClient http = RestClients.withTimeouts(Duration.ofSeconds(20));

    public EmailFetcher(EmailProperties props) {
        this.props = props;
    }

    public List<FetchedEmail> fetch(String accessToken, int limit) {
        try {
            URI listUri = UriComponentsBuilder.fromUriString(API)
                    .queryParam("q", props.searchQuery())
                    .queryParam("maxResults", limit)
                    .build().encode().toUri();
            JsonNode list = get(listUri, accessToken);

            List<FetchedEmail> emails = new ArrayList<>();
            for (JsonNode ref : list.path("messages")) {
                URI messageUri = UriComponentsBuilder.fromUriString(API + "/" + ref.path("id").asText())
                        .queryParam("format", "full")
                        .build().toUri();
                emails.add(parse(ref.path("id").asText(), get(messageUri, accessToken)));
            }
            return emails;
        } catch (RestClientResponseException e) {
            // 403 here usually means the Gmail API isn't enabled in the Google Cloud project.
            log.warn("Gmail API error {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 300));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gmail API error (" + e.getStatusCode().value() + ")");
        }
    }

    private JsonNode get(URI uri, String accessToken) {
        return http.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
    }

    private FetchedEmail parse(String id, JsonNode message) {
        JsonNode payload = message.path("payload");
        long internalDate = message.path("internalDate").asLong(0);
        LocalDate date = internalDate > 0
                ? Instant.ofEpochMilli(internalDate).atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        return new FetchedEmail(id, header(payload, "Subject"), header(payload, "From"), date,
                truncate(extractBody(payload), props.maxBodyChars()));
    }

    private static String header(JsonNode payload, String name) {
        for (JsonNode h : payload.path("headers")) {
            if (name.equalsIgnoreCase(h.path("name").asText())) {
                return h.path("value").asText("");
            }
        }
        return "";
    }

    /** Prefers the text/plain part; falls back to a tag-stripped text/html part. */
    private static String extractBody(JsonNode payload) {
        String plain = findPart(payload, "text/plain");
        if (plain != null && !plain.isBlank()) {
            return plain.replaceAll("\\s+", " ").trim();
        }
        String html = findPart(payload, "text/html");
        return html == null ? "" : htmlToText(html);
    }

    private static String findPart(JsonNode part, String mimeType) {
        if (mimeType.equalsIgnoreCase(part.path("mimeType").asText()) && part.path("body").hasNonNull("data")) {
            byte[] bytes = Base64.getUrlDecoder().decode(part.path("body").path("data").asText());
            return new String(bytes, StandardCharsets.UTF_8);
        }
        for (JsonNode child : part.path("parts")) {
            String found = findPart(child, mimeType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String htmlToText(String html) {
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
        text = decodeEntities(text);
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String s) {
        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            try {
                int codePoint = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
                replacement = new String(Character.toChars(codePoint)); // e.g. &#8377; -> the rupee sign
            } catch (IllegalArgumentException e) {
                replacement = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
