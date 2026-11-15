package mailsender;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MailSenderTest {

    private static MailConfig testConfig() {
        return new MailConfig("smtp.example.com", 587, "sender@example.com", "not-a-real-password",
                null, "Example Sender", true);
    }

    @Test
    void configFallsBackToUsernameAsSender() {
        assertEquals("sender@example.com", testConfig().getFromAddress());
    }

    @Test
    void configNeverPrintsPassword() {
        assertFalse(testConfig().toString().contains("not-a-real-password"));
    }

    @Test
    void environmentOverridesPropertiesFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mail.properties");
        Files.writeString(file, "mail.host=smtp.file.example\nmail.username=file@example.com\nmail.password=x\n");

        MailConfig config = MailConfig.load(file, Map.of("MAIL_HOST", "smtp.env.example"));

        assertEquals("smtp.env.example", config.getHost());
        assertEquals("file@example.com", config.getUsername());
        assertEquals(587, config.getPort());
    }

    @Test
    void missingSettingIsReported(@TempDir Path dir) {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> MailConfig.load(dir.resolve("missing.properties"), Map.of()));
        assertTrue(e.getMessage().contains("host"));
    }

    @Test
    void buildsMessageWithRecipientsSubjectAndAttachment(@TempDir Path dir) throws Exception {
        Path attachment = dir.resolve("report.txt");
        Files.writeString(attachment, "hello");

        MailSender sender = new MailSender(testConfig())
                .to("a@example.com", "b@example.com")
                .cc("c@example.com")
                .bcc("d@example.com")
                .subject("Месечен отчет")
                .html("<p>Здравейте</p>")
                .attach(attachment);

        MimeMessage message = sender.buildMessage(sender.createSession());

        assertEquals(2, message.getRecipients(Message.RecipientType.TO).length);
        assertEquals(1, message.getRecipients(Message.RecipientType.CC).length);
        assertEquals(1, message.getRecipients(Message.RecipientType.BCC).length);
        assertEquals("Месечен отчет", message.getSubject());
        assertTrue(message.getFrom()[0].toString().contains("sender@example.com"));

        Multipart content = (Multipart) message.getContent();
        assertEquals(2, content.getCount());
        assertEquals("report.txt", content.getBodyPart(1).getFileName());
    }

    @Test
    void sendWithoutRecipientsFailsWithError() {
        MailSender sender = new MailSender(testConfig()).subject("No recipients");
        assertFalse(sender.send());
        assertTrue(sender.getError().contains("No recipients"));
    }

    @Test
    void invalidAddressIsRejected() {
        MailSender sender = new MailSender(testConfig()).to("not an address");
        assertFalse(sender.send());
        assertTrue(sender.getError().contains("Invalid e-mail address"));
    }

    @Test
    void missingAttachmentIsRejected(@TempDir Path dir) {
        MailSender sender = new MailSender(testConfig()).to("a@example.com").attach(dir.resolve("none.pdf"));
        assertFalse(sender.send());
        assertTrue(sender.getError().contains("Attachment not found"));
    }
}
