package com.example.leaveplatform.mail;

/**
 * What one SMTP connection needs, assembled from the settings row at the moment
 * a message is sent.
 *
 * <p>A record rather than the entity, so nothing that holds a decrypted password
 * can be handed to the code that reads the database, and nothing that reads the
 * database has to know what a password is for.
 */
record SmtpConfig(String host, int port, String username, String password, boolean useTls) {
}
