package crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

// A RSA keypair
// ChatGPT public free version was heavily used to load, config and persist methods
public class KeyPair {
    private java.security.KeyPair pair;
    // Files are stored in the configuration folder
    private static final Path PUBLIC_KEY_FILE = getConfigDir().resolve("public_key");
    private static final Path PRIVATE_KEY_FILE = getConfigDir().resolve("private_key");

    public KeyPair(java.security.KeyPair kp) {
        this.pair = kp;
    }

    public PublicKey getPublic() {
        return pair.getPublic();
    }

    public PrivateKey getPrivate() {
        return pair.getPrivate();
    }

    public static boolean aPairExists() {
        return Files.exists(PUBLIC_KEY_FILE) && Files.exists(PRIVATE_KEY_FILE);
    }

    public static KeyPair load() {
        try {
            byte[] publicBytes = Files.readAllBytes(PUBLIC_KEY_FILE);
            byte[] privateBytes = Files.readAllBytes(PRIVATE_KEY_FILE);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            return new KeyPair(new java.security.KeyPair(publicKey, privateKey));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load key pair", e);
        }
    }

    public void persistToFile() {
        try {
            Files.write(PUBLIC_KEY_FILE, pair.getPublic().getEncoded());
            Files.write(PRIVATE_KEY_FILE, pair.getPrivate().getEncoded());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save key pair", e);
        }
    }

    public static Path getConfigDir() {
        Path path = findConfigDir();
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return path;
    }

    public static Path findConfigDir() {
        String appName = "poneytoponey";
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData, appName);

        } else if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", appName);

        } else {
            // Linux / Unix
            String xdg = System.getenv("XDG_CONFIG_HOME");

            if (xdg != null && !xdg.isBlank()) {
                return Paths.get(xdg, appName);
            }

            return Paths.get(home, ".config", appName);
        }
    }
}
