# MailSender v1

A small Java library and command-line tool for sending e-mail through any SMTP server
(STARTTLS + authentication): plain text or HTML bodies, multiple recipients, Cc/Bcc,
attachments and full UTF-8 support (subjects, bodies and sender names, e.g. Cyrillic).

Credentials are **never** stored in the source code. They come from a local
`mail.properties` file (excluded from git) or from environment variables.

## Requirements

- Java 11 or newer
- Maven 3.6+

Dependencies: Jakarta Mail 2.1 with the Eclipse Angus implementation; JUnit 5 for tests.

## Build

```
mvn package
```

This runs the unit tests and creates `target/mailsender-1.0.0-jar-with-dependencies.jar`.

## Configure

```
cp mail.properties.example mail.properties
```

Then edit `mail.properties`, or set environment variables instead (they take priority):

| Property | Environment variable | Default |
| --- | --- | --- |
| `mail.host` | `MAIL_HOST` | none (required) |
| `mail.port` | `MAIL_PORT` | 587 |
| `mail.username` | `MAIL_USERNAME` | none (required) |
| `mail.password` | `MAIL_PASSWORD` | none (required) |
| `mail.from` | `MAIL_FROM` | same as username |
| `mail.from.name` | `MAIL_FROM_NAME` | empty |
| `mail.starttls` | — | true |

For Gmail, use `smtp.gmail.com`, port 587 and an
[App Password](https://support.google.com/accounts/answer/185833), not your account password.

## Command line

```
java -jar target/mailsender-1.0.0-jar-with-dependencies.jar \
     --to alice@example.com --cc bob@example.com \
     --subject "Monthly report" \
     --body-file report.html --html \
     --attach report.pdf
```

| Option | Meaning |
| --- | --- |
| `--to ADDRESS` | recipient (repeatable) |
| `--cc ADDRESS` | copy (repeatable) |
| `--bcc ADDRESS` | blind copy (repeatable) |
| `--subject TEXT` | subject |
| `--body TEXT` | message body |
| `--body-file FILE` | read the body from a UTF-8 file |
| `--html` | treat the body as HTML |
| `--attach FILE` | attachment (repeatable) |
| `--config FILE` | settings file (default `mail.properties`) |

Exit codes: 0 sent, 1 invalid arguments or settings, 2 the server rejected the message.

## Library use

```java
MailSender sender = new MailSender(MailConfig.load())
        .to("alice@example.com")
        .subject("Monthly report")
        .html("<h1>Report</h1><p>See the attachment.</p>")
        .attach(Path.of("report.pdf"));

if (!sender.send()) {
    System.err.println(sender.getError());
}
```

`send()` returns `false` instead of throwing, and `getError()` explains why:
no recipients, an invalid address, a missing attachment, or an SMTP error.

## Tests

`mvn test` covers configuration loading and overrides, building the MIME message
(recipients, UTF-8 subject, attachments) and the validation errors. The tests never
connect to a mail server.

## Changes from the original version

- Removed hard-coded credentials and addresses; settings now come from a file or the environment
- `send()` now reports failures (it previously always returned `true`)
- Validates recipients and attachments before connecting
- Sender defaults to the authenticated account instead of a fixed address
- Multiple attachments; attachment names work on Windows and Linux
- Jakarta Mail 2 via Maven instead of a bundled `javax.mail.jar`
- Connection and read timeouts
- Unit tests
