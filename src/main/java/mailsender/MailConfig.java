package mailsender;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * SMTP settings.
 *
 * Values are read from a properties file (by default "mail.properties" in the
 * working directory) and can be overridden by environment variables:
 *
 *   MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_FROM_NAME
 *
 * Credentials never live in the source code. Keep mail.properties out of version
 * control (it is listed in .gitignore); mail.properties.example shows the format.
 */
public final class MailConfig {

    public static final String DEFAULT_FILE = "mail.properties";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;
    private final boolean startTls;

    public MailConfig(String host, int port, String username, String password,
                      String fromAddress, String fromName, boolean startTls) {
        this.host = require(host, "host");
        this.port = port;
        this.username = require(username, "username");
        this.password = require(password, "password");
        // Send from the authenticated account unless another sender is configured
        this.fromAddress = isBlank(fromAddress) ? username : fromAddress;
        this.fromName = fromName;
        this.startTls = startTls;
    }

    /** Loads settings from the default file, then applies environment variable overrides. */
    public static MailConfig load() throws IOException {
        return load(Path.of(DEFAULT_FILE), System.getenv());
    }

    /**
     * Loads settings from the given properties file (it may be missing when
     * everything comes from the environment), then applies the overrides.
     */
    public static MailConfig load(Path file, Map<String, String> env) throws IOException {
        Properties p = new Properties();
        if (file != null && Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }

        String host = pick(env, "MAIL_HOST", p.getProperty("mail.host"));
        String port = pick(env, "MAIL_PORT", p.getProperty("mail.port", "587"));
        String user = pick(env, "MAIL_USERNAME", p.getProperty("mail.username"));
        String pass = pick(env, "MAIL_PASSWORD", p.getProperty("mail.password"));
        String from = pick(env, "MAIL_FROM", p.getProperty("mail.from"));
        String name = pick(env, "MAIL_FROM_NAME", p.getProperty("mail.from.name"));
        boolean tls = Boolean.parseBoolean(p.getProperty("mail.starttls", "true"));

        try {
            return new MailConfig(host, Integer.parseInt(port.trim()), user, pass, from, name, tls);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid SMTP port: " + port);
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFromAddress() { return fromAddress; }
    public String getFromName() { return fromName; }
    public boolean isStartTls() { return startTls; }

    @Override
    public String toString() {
        // Never print the password
        return "MailConfig{host=" + host + ", port=" + port + ", username=" + username
                + ", from=" + fromAddress + ", startTls=" + startTls + "}";
    }

    // ------------------------------------------------------------------

    private static String pick(Map<String, String> env, String envName, String fallback) {
        String value = env.get(envName);
        return isBlank(value) ? fallback : value;
    }

    private static String require(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Missing SMTP setting: " + name
                    + " (set it in " + DEFAULT_FILE + " or via environment variables)");
        }
        return value;
    }

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
