package directory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import crypto.RSA;

public class DirectoryServer {

    private static final Logger LOGGER = Logger.getLogger(DirectoryServer.class.getName());
    static RSA rsa = new RSA();;

    static private class Content {

        final private String ip;
        final private String pubkey;

        public Content(String ip, String pubkey) {
            this.ip = ip;
            this.pubkey = pubkey;
        }

        public String getIp() {
            return ip;
        }

        public String getPubkey() {
            return pubkey;
        }
    }

    // username -> ip
    private static final Map<String, Content> directory = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/list", DirectoryServer::handleList);
        server.createContext("/register/challenge", DirectoryServer::handleRegisterChallenge);
        server.createContext("/register/confirm", DirectoryServer::handleRegisterConfirm);
        server.createContext("/remove/challenge", DirectoryServer::handleRemoveChallenge);
        server.createContext("/remove/confirm", DirectoryServer::handleRemoveConfirm);

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        String ip = InetAddress.getLocalHost().getHostAddress();
        LOGGER.info("Directory server started on http://" + ip + ":" + port);
    }

    // ---------------- LIST ----------------

    private static void handleList(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 405, "method not allowed");
            return;
        }

        if (directory.isEmpty()) {
            sendEmpty(exchange, 204, "directory is empty");
            return;
        }

        StringBuilder response = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Content> entry : directory.entrySet()) {
            if (!first)
                response.append("\n");
            response.append(EntryToStringLine(entry));
            first = false;
        }

        sendText(exchange, 200, response.toString(), "listed " + directory.size() + " entries");
    }

    // ---------------- REGISTER ----------------

    private static void handleRegisterChallenge(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 405, "method not allowed");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String[] lines = body.split("\\R");

        if (lines.length != 2) {
            sendText(exchange, 400, "Invalid register challenge format", "invalid register challenge payload");
            return;
        }

        String username = lines[0].trim();
        String pubkey = lines[1].trim();

        if (username.isEmpty() || pubkey.isEmpty()) {
            sendEmpty(exchange, 400, "blank register challenge fields");
            return;
        }

        try {
            rsa.decodePublicKey(pubkey);
        } catch (GeneralSecurityException e) {
            sendEmpty(exchange, 400, "invalid public key for username " + username);
            return;
        }

        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();

        if (directory.containsKey(username)) {
            sendEmpty(exchange, 409, "username already registered: " + username);
            return;
        }

        if (!IsUniqueIp(ip)) {
            sendEmpty(exchange, 409, "ip already registered: " + ip);
            return;
        }

        String challenge = generateChallenge();
        registerChallenges.put(
                username,
                new RegisterChallenge(challenge, pubkey, ip, System.currentTimeMillis()));

        sendText(exchange, 200, challenge, "issued register challenge for username " + username + " from " + ip);
    }

    private static void handleRegisterConfirm(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 405, "method not allowed");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String[] lines = body.split("\\R");

        if (lines.length != 3) {
            sendText(exchange, 400, "Invalid register confirm format", "invalid register confirm payload");
            return;
        }

        String username = lines[0].trim();
        String pubkey = lines[1].trim();
        String signatureBase64 = lines[2].trim();

        if (username.isEmpty() || pubkey.isEmpty() || signatureBase64.isEmpty()) {
            sendEmpty(exchange, 400, "blank register confirm fields");
            return;
        }

        RegisterChallenge registerChallenge = registerChallenges.get(username);

        if (registerChallenge == null) {
            sendText(exchange, 401, "No challenge requested",
                    "register confirm without challenge for username " + username);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - registerChallenge.createdAt > CHALLENGE_TTL_MS) {
            registerChallenges.remove(username);
            sendText(exchange, 401, "Challenge expired", "expired register challenge for username " + username);
            return;
        }

        if (!registerChallenge.pubkey.equals(pubkey)) {
            sendText(exchange, 401, "Public key mismatch",
                    "register confirm public key mismatch for username " + username);
            return;
        }

        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!registerChallenge.ip.equals(ip)) {
            sendText(exchange, 401, "IP mismatch", "register confirm ip mismatch for username " + username);
            return;
        }

        PublicKey publicKey;
        byte[] signatureBytes;

        try {
            publicKey = rsa.decodePublicKey(pubkey);
            signatureBytes = Base64.getDecoder().decode(signatureBase64);
        } catch (GeneralSecurityException e) {
            sendEmpty(exchange, 400, "invalid public key or signature encoding for username " + username);
            return;
        }

        String signedContentText = "REGISTER / "
                + username + " / "
                + pubkey + " / "
                + registerChallenge.challenge;

        boolean valid = rsa.verifySignature(
                signatureBytes,
                signedContentText.getBytes(StandardCharsets.UTF_8),
                publicKey);

        if (!valid) {
            sendEmpty(exchange, 401, "invalid register signature for username " + username);
            return;
        }

        if (directory.containsKey(username)) {
            sendEmpty(exchange, 409, "username already registered: " + username);
            return;
        }

        if (!IsUniqueIp(ip)) {
            sendEmpty(exchange, 409, "ip already registered: " + ip);
            return;
        }

        directory.put(username, new Content(ip, pubkey));
        registerChallenges.remove(username);

        String response = formatEntryLine(username, ip, pubkey);
        sendText(exchange, 201, response, "registered username " + username + " from " + ip);
    }

    // ---------------- REMOVE ----------------

    private static void handleRemoveChallenge(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 405, "method not allowed");
            return;
        }

        String username = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8).trim();

        Content content = directory.get(username);

        if (content == null) {
            sendEmpty(exchange, 404, "remove challenge requested for unknown username " + username);
            return;
        }

        String challenge = generateChallenge();

        leaveChallenges.put(
                username,
                new LeaveChallenge(challenge, System.currentTimeMillis()));

        sendText(exchange, 200, challenge, "issued leave challenge for username " + username);
    }

    private static void handleRemoveConfirm(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 405, "method not allowed");
            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String[] lines = body.split("\\R");

        if (lines.length != 2) {
            sendText(exchange, 400, "Invalid remove confirm format", "invalid remove confirm payload");
            return;
        }

        String username = lines[0].trim();
        String signatureBase64 = lines[1].trim();

        Content content = directory.get(username);

        if (content == null) {
            sendEmpty(exchange, 404, "remove confirm requested for unknown username " + username);
            return;
        }

        LeaveChallenge leaveChallenge = leaveChallenges.get(username);

        if (leaveChallenge == null) {
            sendText(exchange, 401, "No challenge requested",
                    "remove confirm without challenge for username " + username);
            return;
        }

        long now = System.currentTimeMillis();

        if (now - leaveChallenge.createdAt > CHALLENGE_TTL_MS) {
            leaveChallenges.remove(username);
            sendText(exchange, 401, "Challenge expired", "expired leave challenge for username " + username);
            return;
        }

        String signedContentText = "LEAVE / " +
                username + " / " +
                leaveChallenge.challenge;

        byte[] signedContent = signedContentText.getBytes(StandardCharsets.UTF_8);

        byte[] signature;

        try {
            signature = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException e) {
            sendText(exchange, 400, "Invalid signature encoding",
                    "invalid remove signature encoding for username " + username);
            return;
        }

        PublicKey publicKey;

        try {
            publicKey = rsa.decodePublicKey(content.pubkey);
        } catch (GeneralSecurityException e) {
            sendText(exchange, 500, "Stored public key is invalid",
                    "stored public key is invalid for username " + username);
            return;
        }

        boolean valid = rsa.verifySignature(
                signature,
                signedContent,
                publicKey);

        if (!valid) {
            sendText(exchange, 401, "Invalid signature", "invalid leave signature for username " + username);
            return;
        }

        leaveChallenges.remove(username);
        directory.remove(username);

        String response = formatEntryLine(username, content.ip, content.pubkey);
        sendText(exchange, 200, response, "removed username " + username + " from " + content.ip);
    }

    // ---------------- UTILS ----------------

    private static String readBody(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendEmpty(HttpExchange exchange, int status, String message) throws IOException {
        logOutcome(exchange, status, message);
        exchange.sendResponseHeaders(status, -1);
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        sendText(exchange, status, text, "");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String message) throws IOException {
        logOutcome(exchange, status, message);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void logOutcome(HttpExchange exchange, int status, String message) {
        String requestLine = exchange.getRequestMethod()
                + " "
                + exchange.getRequestURI()
                + " from "
                + exchange.getRemoteAddress().getAddress().getHostAddress();
        String suffix = message == null || message.isBlank() ? "" : " - " + message;
        String line = requestLine + " -> " + status + suffix;

        if (status >= 500) {
            LOGGER.severe(line);
        } else if (status >= 400) {
            LOGGER.warning(line);
        } else {
            LOGGER.info(line);
        }
    }

    private static String EntryToStringLine(Map.Entry<String, Content> entry) {
        return EntryToStringLine(entry.getKey(), entry.getValue().getIp(), entry.getValue().getPubkey());
    }

    private static String EntryToStringLine(String username, String ip, String pubkey) {
        return formatEntryLine(username, ip, pubkey);
    }

    private static String formatEntryLine(String username, String ip, String pubkey) {
        return username + " / " + ip + " / " + pubkey;
    }

    private static boolean IsUniqueIp(String ip) {
        for (Map.Entry<String, Content> entry : directory.entrySet()) {
            if (entry.getValue().ip.equals(ip)) {
                return false;
            }
        }
        return true;
    }

    private static final SecureRandom secureRandom = new SecureRandom();

    private static String generateChallenge() {
        byte[] bytes = new byte[32]; // 256-bit challenge
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static final Map<String, LeaveChallenge> leaveChallenges = new ConcurrentHashMap<>();
    private static final Map<String, RegisterChallenge> registerChallenges = new ConcurrentHashMap<>();

    private static final long CHALLENGE_TTL_MS = 30_000;

    private static class RegisterChallenge {
        final String challenge;
        final String pubkey;
        final String ip;
        final long createdAt;

        RegisterChallenge(String challenge, String pubkey, String ip, long createdAt) {
            this.challenge = challenge;
            this.pubkey = pubkey;
            this.ip = ip;
            this.createdAt = createdAt;
        }
    }

    private static class LeaveChallenge {
        String challenge;
        long createdAt;

        LeaveChallenge(String challenge, long createdAt) {
            this.challenge = challenge;
            this.createdAt = createdAt;
        }
    }

}
