package poneytoponey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import java.util.Optional;

import crypto.KeyPair;
import crypto.RSA;

public class Directory {

    private final String host;
    private final RSA rsa;
    private String registeredUsername;

    public Directory(String host) {
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        try {
            this.rsa = new RSA();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize directory crypto", e);
        }
    }

    public List<Entry> list() {
        HttpURLConnection connection = null;

        try {
            connection = openConnection("/list", "GET");

            int status = connection.getResponseCode();

            if (status == 204) {
                return new ArrayList<>();
            }

            if (status != 200) {
                throw new RuntimeException("list failed with HTTP status " + status);
            }

            String body = readStream(connection.getInputStream());
            List<Entry> result = new ArrayList<>();

            if (body.isBlank()) {
                return result;
            }

            String[] lines = body.split("\\R");
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\s*/\\s*", 3);
                if (parts.length >= 2) {
                    String pubkey = parts.length == 3 ? parts[2].trim() : "";
                    result.add(new Entry(parts[0].trim(), parts[1].trim(), rsa.decodePublicKey(pubkey)));
                }
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void join(String username, KeyPair keyPair) throws Exception {
        HttpURLConnection connection = null;
        String normalizedUsername = requireUsername(username);
        String publicKeyBase64 = encodePublicKey(requireKeyPair(keyPair));
        String challenge = requestRegisterChallenge(normalizedUsername, publicKeyBase64);
        String signedContent = "REGISTER / " + normalizedUsername + " / " + publicKeyBase64 + " / " + challenge;
        String requestBody = normalizedUsername + "\n" + publicKeyBase64 + "\n"
                + buildRegisterSignature(signedContent, keyPair);

        try {
            connection = openConnection("/register/confirm", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");

            writeBody(connection, requestBody);

            int status = connection.getResponseCode();

            if (status != 201) {
                throw httpFailure("Global directory join", status, connection);
            }

            this.registeredUsername = normalizedUsername;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected String requestRegisterChallenge(String username, String publicKeyBase64) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = openConnection("/register/challenge", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            writeBody(connection, username + "\n" + publicKeyBase64);

            int status = connection.getResponseCode();

            if (status != 200) {
                throw httpFailure("register challenge", status, connection);
            }

            String challenge = readStream(connection.getInputStream()).trim();
            if (challenge.isEmpty()) {
                throw new RuntimeException("register challenge failed: empty challenge");
            }
            return challenge;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void leave(KeyPair keyPair) throws Exception {
        if (registeredUsername == null) {
            return;
        }

        String username = registeredUsername;
        String challenge = requestLeaveChallenge(username);
        String signatureBase64 = buildLeaveSignature("LEAVE / " + username + " / " + challenge, keyPair);
        HttpURLConnection connection = null;

        try {
            connection = openConnection("/remove/confirm", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            writeBody(connection, username + "\n" + signatureBase64);

            int status = connection.getResponseCode();

            if (status != 200) {
                throw httpFailure("leave", status, connection);
            }

            registeredUsername = null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;

            while ((line = reader.readLine()) != null) {
                if (!first) {
                    sb.append("\n");
                }
                sb.append(line);
                first = false;
            }
        }

        return sb.toString();
    }

    public void removeUser(String username, KeyPair keyPair) throws Exception {
        String normalizedUsername = requireUsername(username);

        if (registeredUsername == null || !registeredUsername.equals(normalizedUsername)) {
            return;
        }

        leave(keyPair);
    }

    protected HttpURLConnection openConnection(String path, String method) throws Exception {
        URI uri = new URI(host + path);
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        return connection;
    }

    protected void writeBody(HttpURLConnection connection, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }
    }

    protected String requestLeaveChallenge(String username) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = openConnection("/remove/challenge", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            writeBody(connection, username);

            int status = connection.getResponseCode();

            if (status != 200) {
                throw httpFailure("leave challenge", status, connection);
            }

            String challenge = readStream(connection.getInputStream()).trim();
            if (challenge.isEmpty()) {
                throw new RuntimeException("leave challenge failed: empty challenge");
            }
            return challenge;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected String buildRegisterSignature(String signedContent, KeyPair keyPair) {
        return signBase64(signedContent, requireKeyPair(keyPair));
    }

    protected String buildLeaveSignature(String signedContent, KeyPair keyPair) {
        return signBase64(signedContent, requireKeyPair(keyPair));
    }

    protected String signBase64(String content, KeyPair keyPair) {
        byte[] signature = rsa.sign(content.getBytes(StandardCharsets.UTF_8), keyPair.getPrivate());
        return Base64.getEncoder().encodeToString(signature);
    }

    protected KeyPair requireKeyPair(KeyPair keyPair) {
        if (keyPair == null) {
            throw new IllegalArgumentException("keyPair must not be null");
        }
        return keyPair;
    }

    protected String encodePublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    protected String requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return username.trim();
    }

    protected RuntimeException httpFailure(String action, int status, HttpURLConnection connection) throws IOException {
        String body = "";
        InputStream errorStream = connection.getErrorStream();
        if (errorStream != null) {
            body = readStream(errorStream).trim();
        }

        if (body.isEmpty()) {
            return new RuntimeException(action + " failed with HTTP status " + status);
        }

        return new RuntimeException(action + " failed with HTTP status " + status + ": " + body);
    }
}
