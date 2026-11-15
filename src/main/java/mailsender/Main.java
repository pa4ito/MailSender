package mailsender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line front end.
 *
 * java -jar mailsender.jar --to a@example.com [--to b@example.com] [--cc ...] [--bcc ...]
 *                          [--subject "Text"] [--body "Text" | --body-file message.html]
 *                          [--html] [--attach file.pdf ...] [--config mail.properties]
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help")) {
            printHelp();
            return;
        }

        Path configFile = Path.of(MailConfig.DEFAULT_FILE);
        String subject = "";
        String body = "";
        boolean html = false;
        MailSenderArgs recipients = new MailSenderArgs();

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--to":        recipients.to.add(value(args, ++i)); break;
                    case "--cc":        recipients.cc.add(value(args, ++i)); break;
                    case "--bcc":       recipients.bcc.add(value(args, ++i)); break;
                    case "--subject":   subject = value(args, ++i); break;
                    case "--body":      body = value(args, ++i); break;
                    case "--body-file": body = Files.readString(Path.of(value(args, ++i)), StandardCharsets.UTF_8); break;
                    case "--html":      html = true; break;
                    case "--attach":    recipients.attachments.add(Path.of(value(args, ++i))); break;
                    case "--config":    configFile = Path.of(value(args, ++i)); break;
                    default:
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }

            MailSender sender = new MailSender(MailConfig.load(configFile, System.getenv()))
                    .to(recipients.to.toArray(new String[0]))
                    .cc(recipients.cc.toArray(new String[0]))
                    .bcc(recipients.bcc.toArray(new String[0]))
                    .subject(subject)
                    .attach(recipients.attachments.toArray(new Path[0]));
            if (html) {
                sender.html(body);
            } else {
                sender.text(body);
            }

            if (sender.send()) {
                System.out.println("Message sent.");
            } else {
                System.err.println("Sending failed: " + sender.getError());
                System.exit(2);
            }
        } catch (IllegalArgumentException | IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[i - 1]);
        }
        return args[i];
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar mailsender.jar --to ADDRESS [options]");
        System.out.println();
        System.out.println("  --to ADDRESS       recipient (repeatable)");
        System.out.println("  --cc ADDRESS       copy (repeatable)");
        System.out.println("  --bcc ADDRESS      blind copy (repeatable)");
        System.out.println("  --subject TEXT     subject (UTF-8)");
        System.out.println("  --body TEXT        message body");
        System.out.println("  --body-file FILE   read the body from a UTF-8 file");
        System.out.println("  --html             treat the body as HTML");
        System.out.println("  --attach FILE      attachment (repeatable)");
        System.out.println("  --config FILE      settings file (default: mail.properties)");
        System.out.println();
        System.out.println("SMTP settings come from the config file or from the environment variables");
        System.out.println("MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_FROM_NAME.");
    }

    /** Collected repeatable options. */
    private static final class MailSenderArgs {
        final java.util.List<String> to = new java.util.ArrayList<>();
        final java.util.List<String> cc = new java.util.ArrayList<>();
        final java.util.List<String> bcc = new java.util.ArrayList<>();
        final java.util.List<Path> attachments = new java.util.ArrayList<>();
    }
}
