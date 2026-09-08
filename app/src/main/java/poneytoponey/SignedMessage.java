package poneytoponey;

import java.security.PrivateKey;
import java.security.PublicKey;

import crypto.RSA;

public class SignedMessage extends Message {
    private static RSA rsa = new RSA();
    private byte[] signature;

    public SignedMessage(String texte, long senderTimestamp, String author, boolean isImportant,
            PrivateKey privateKey) {
        super(texte, senderTimestamp, author, isImportant);
        byte[] signature = rsa.sign(getBytesToSign(), privateKey);
        this.signature = signature;
    }

    public boolean verifySignature(PublicKey publicKey) {
        return rsa.verifySignature(this.signature, getBytesToSign(), publicKey);
    }

    private byte[] getBytesToSign() {
        return Message.generateBytesToSign(this.getTexte().getBytes(), this.getAuthor().getBytes(),
                this.getSenderTimestamp().toString().getBytes(), this.getIsImportant().toString().getBytes());
    }
}
