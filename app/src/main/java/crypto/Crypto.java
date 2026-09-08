package crypto;

import java.security.PrivateKey;
import java.security.PublicKey;

// Generic Crypto interface for all features we need to access in the application.
// Concrete algorithms are integrated in concrete implementations.
public interface Crypto {
    public byte[] sign(byte[] buffer, PrivateKey privateKey);

    public boolean verifySignature(byte[] signature, byte[] signedContent, PublicKey publicKey);

    public byte[] encrypt(String text, PublicKey publicKey);

    public String decrypt(byte[] cipherText, PrivateKey privateKey);

    public KeyPair generateKeyPair();

    public PublicKey decodePublicKey(String pem) throws Exception;

    public PrivateKey decodePrivateKey(String pem) throws Exception;
}
