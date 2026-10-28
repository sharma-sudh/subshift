package com.subshift.backend.mail;

import com.subshift.backend.common.AppTime;
import com.subshift.backend.email.EmailFetcher;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.email.EmailProperties;
import com.subshift.backend.gmail.GmailConnection;
import com.subshift.backend.gmail.GmailConnectionRepository;
import com.subshift.backend.gmail.TokenEncryptor;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Yahoo Mail over IMAP (TLS, port 993), read-only. Yahoo has no public mail API for small apps, so the user
 * creates an "app password" in their Yahoo account security settings and gives it to SubShift once. It is stored
 * encrypted, used only to read the inbox, and deleted when the user disconnects.
 */
@Slf4j
@Component
public class YahooProvider implements MailProvider {

    private static final int IMAP_PORT = 993;

    private final GmailConnectionRepository connectionRepository;
    private final TokenEncryptor encryptor;
    private final EmailProperties emailProps;
    private final String host;

    public YahooProvider(GmailConnectionRepository connectionRepository, TokenEncryptor encryptor,
                         EmailProperties emailProps,
                         @Value("${email.yahoo.imap-host:imap.mail.yahoo.com}") String host) {
        this.connectionRepository = connectionRepository;
        this.encryptor = encryptor;
        this.emailProps = emailProps;
        this.host = host;
    }

    @Override
    public ProviderType type() {
        return ProviderType.YAHOO;
    }

    @Override
    public MailSession open(Integer userId) {
        GmailConnection connection = connectionRepository.findByUserId(userId)
                .filter(c -> c.getProvider() == ProviderType.YAHOO && c.getAppPasswordEncrypted() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Yahoo Mail is not connected"));
        return connect(connection.getMailboxAddress(), encryptor.decrypt(connection.getAppPasswordEncrypted()), false);
    }

    /** Logs in once to check the address and app password. Throws 400 if Yahoo rejects them. */
    public void verify(String address, String appPassword) {
        connect(address, appPassword, true).close();
    }

    private MailSession connect(String address, String appPassword, boolean firstTime) {
        Properties properties = new Properties();
        properties.put("mail.imaps.connectiontimeout", "10000");
        properties.put("mail.imaps.timeout", "20000");
        properties.put("mail.imaps.writetimeout", "20000");
        Store store = null;
        try {
            store = Session.getInstance(properties).getStore("imaps");
            store.connect(host, IMAP_PORT, address, appPassword);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            return new ImapSession(store, inbox, emailProps);
        } catch (AuthenticationFailedException e) {
            closeQuietly(store);
            throw new ResponseStatusException(firstTime ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT,
                    firstTime ? "Yahoo rejected that address or app password"
                            : "Yahoo rejected the saved login; please reconnect Yahoo Mail");
        } catch (MessagingException | RuntimeException e) {
            closeQuietly(store);
            log.warn("Could not open the Yahoo mailbox: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Yahoo Mail");
        }
    }

    private static void closeQuietly(Store store) {
        if (store != null) {
            try {
                store.close();
            } catch (MessagingException ignored) {
                // nothing useful to do
            }
        }
    }

    private static final class ImapSession implements MailSession {

        private final Store store;
        private final Folder folder;
        private final EmailProperties props;

        ImapSession(Store store, Folder folder, EmailProperties props) {
            this.store = store;
            this.folder = folder;
            this.props = props;
        }

        @Override
        public List<String> listMessageIds(Instant since, int max) {
            Instant from = since != null ? since : Instant.now().minus(props.lookbackDays(), ChronoUnit.DAYS);
            try {
                SearchTerm subject = new OrTerm(props.subjectKeywords().stream()
                        .map(SubjectTerm::new).toArray(SearchTerm[]::new));
                // IMAP dates have day precision, so the exact cut-off is applied again below.
                SearchTerm term = new AndTerm(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(from)), subject);
                Message[] found = folder.search(term);
                FetchProfile profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                folder.fetch(found, profile);

                UIDFolder uids = (UIDFolder) folder;
                List<Message> recent = new ArrayList<>();
                for (Message message : found) {
                    Instant received = receivedAt(message);
                    if (received != null && received.isAfter(from)) {
                        recent.add(message);
                    }
                }
                recent.sort(Comparator.comparing((Message m) -> receivedAt(m)).reversed());
                List<String> ids = new ArrayList<>();
                for (Message message : recent.subList(0, Math.min(max, recent.size()))) {
                    ids.add(String.valueOf(uids.getUID(message)));
                }
                return ids;
            } catch (MessagingException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Yahoo Mail returned an error");
            }
        }

        @Override
        public FetchedEmail fetchMessage(String id) {
            try {
                Message message = ((UIDFolder) folder).getMessageByUID(Long.parseLong(id));
                if (message == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "An email disappeared from Yahoo Mail");
                }
                Instant received = receivedAt(message);
                if (received == null) {
                    received = Instant.now();
                }
                Object from = message.getFrom() == null || message.getFrom().length == 0 ? "" : message.getFrom()[0];
                return new FetchedEmail(id, message.getSubject() == null ? "" : message.getSubject(),
                        String.valueOf(from), AppTime.dateOf(received), received,
                        EmailFetcher.truncate(bodyText(message), props.maxBodyChars()));
            } catch (MessagingException | IOException | NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Yahoo Mail returned an error");
            }
        }

        @Override
        public void close() {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            } catch (MessagingException ignored) {
                // closing is best effort
            }
            closeQuietly(store);
        }

        private static Instant receivedAt(Message message) {
            try {
                Date date = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
                return date == null ? null : date.toInstant();
            } catch (MessagingException e) {
                return null;
            }
        }

        /** Prefers the plain-text part; falls back to the HTML part with the tags stripped. */
        private static String bodyText(Part part) throws MessagingException, IOException {
            String plain = find(part, "text/plain");
            if (plain != null && !plain.isBlank()) {
                return plain.replaceAll("\\s+", " ").trim();
            }
            String html = find(part, "text/html");
            return html == null ? "" : EmailFetcher.htmlToText(html);
        }

        private static String find(Part part, String mimeType) throws MessagingException, IOException {
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                return null;
            }
            if (part.isMimeType(mimeType)) {
                Object content = part.getContent();
                return content instanceof String text ? text : null;
            }
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    String found = find(multipart.getBodyPart(i), mimeType);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
    }
}
