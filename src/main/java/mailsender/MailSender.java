package mailsender;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Sends e-mails through an SMTP server (STARTTLS + authentication).
 *
 * <pre>
 * MailSender sender = new MailSender(MailConfig.load())
 *         .to("someone@example.com")
 *         .subject("Monthly report")
 *         .html("&lt;h1&gt;Report&lt;/h1&gt;&lt;p&gt;See the attachment.&lt;/p&gt;")
 *         .attach(Path.of("report.pdf"));
 *
 * if (!sender.send()) {
 *     System.err.println(sender.getError());
 * }
 * </pre>
 */
public class MailSender {

    private final MailConfig config;

    private final List<String> to = new ArrayList<>();
    private final List<String> cc = new ArrayList<>();
    private final List<String> bcc = new ArrayList<>();
    private final List<Path> attachments = new ArrayList<>();
    private String subject = "";
    private String body = "";
    private String contentType = "text/plain; charset=UTF-8";
    private String error;

    public MailSender(MailConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    // ------------------------------------------------------------ setters

    public MailSender to(String... addresses) { to.addAll(Arrays.asList(addresses)); return this; }

    public MailSender cc(String... addresses) { cc.addAll(Arrays.asList(addresses)); return this; }

    public MailSender bcc(String... addresses) { bcc.addAll(Arrays.asList(addresses)); return this; }

    public MailSender subject(String subject) { this.subject = subject; return this; }

    /** Plain-text body. */
    public MailSender text(String body) {
        this.body = body;
        this.contentType = "text/plain; charset=UTF-8";
        return this;
    }

    /** HTML body. */
    public MailSender html(String body) {
        this.body = body;
        this.contentType = "text/html; charset=UTF-8";
        return this;
    }

    public MailSender attach(Path... files) { attachments.addAll(Arrays.asList(files)); return this; }

    public List<Path> getAttachments() { return Collections.unmodifiableList(attachments); }

    /** The reason for the last failed send(), or null after a successful one. */
    public String getError() { return error; }

    // --------------------------------------------------------------- send

    /**
     * Sends the message.
     *
     * @return true if the SMTP server accepted the message; otherwise false,
     *         with the reason available from {@link #getError()}
     */
    public boolean send() {
        error = null;
        try {
            Transport.send(buildMessage(createSession()));
            return true;
        } catch (MessagingException | UnsupportedEncodingException | IllegalStateException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
    }

    // ------------------------------------------------------------ helpers

    Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(config.isStartTls()));
        props.put("mail.smtp.starttls.required", String.valueOf(config.isStartTls()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getUsername(), config.getPassword());
            }
        });
    }

    /** Builds the MIME message without sending it (also used by the tests). */
    MimeMessage buildMessage(Session session) throws MessagingException, UnsupportedEncodingException {
        if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
            throw new IllegalStateException("No recipients: add at least one To, Cc or Bcc address");
        }
        for (Path file : attachments) {
            if (!Files.isReadable(file)) {
                throw new IllegalStateException("Attachment not found or not readable: " + file);
            }
        }

        MimeMessage message = new MimeMessage(session);

        if (MailConfig.isBlank(config.getFromName())) {
            message.setFrom(new InternetAddress(config.getFromAddress()));
        } else {
            message.setFrom(new InternetAddress(config.getFromAddress(), config.getFromName(), "UTF-8"));
        }
        setRecipients(message, Message.RecipientType.TO, to);
        setRecipients(message, Message.RecipientType.CC, cc);
        setRecipients(message, Message.RecipientType.BCC, bcc);
        message.setSubject(subject, "UTF-8");

        Multipart multipart = new MimeMultipart();

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(body, contentType);
        multipart.addBodyPart(bodyPart);

        for (Path file : attachments) {
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.setDataHandler(new DataHandler(new FileDataSource(file.toFile())));
            attachment.setFileName(file.getFileName().toString());
            multipart.addBodyPart(attachment);
        }

        message.setContent(multipart);
        message.saveChanges();
        return message;
    }

    private static void setRecipients(MimeMessage message, Message.RecipientType type,
                                      List<String> addresses) throws MessagingException {
        if (addresses.isEmpty()) {
            return;
        }
        InternetAddress[] parsed = new InternetAddress[addresses.size()];
        for (int i = 0; i < parsed.length; i++) {
            try {
                parsed[i] = new InternetAddress(addresses.get(i), true);
            } catch (AddressException e) {
                throw new AddressException("Invalid e-mail address: " + addresses.get(i));
            }
        }
        message.setRecipients(type, parsed);
    }
}
