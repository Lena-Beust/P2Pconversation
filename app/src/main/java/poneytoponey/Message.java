package poneytoponey;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.UUID; //D2

public class Message implements Serializable {

    private static final long serialVersionUID = 1L; // D2 // lui aussi en sérialisable pour pouvoir etre mis en bytes
    // dans le disque

    private String texte;
    private long senderTimestamp; // D1
    private String author;
    private boolean isImportant; // M1
    private UUID uuid; // D1

    public Message(String texte, long senderTimestamp, String author, boolean isImportant) {
        this.texte = texte;
        this.senderTimestamp = senderTimestamp;
        this.author = author;
        this.isImportant = isImportant; // M1
        this.uuid = UUID.randomUUID(); // D1;
    }

    public Message(UUID uuid, String text, long timestamp, String author, boolean important) {
        this(text, timestamp, author, important);
        this.uuid = uuid;
    }

    public String getTexte() {
        return this.texte;
    }

    public Long getSenderTimestamp() {
        return this.senderTimestamp;
    }

    public String getAuthor() {
        return this.author;
    }

    // M1
    public Boolean getIsImportant() {
        return this.isImportant;
    }

    // D1
    public UUID getUuid() {
        return this.uuid;
    }

    public static byte[] generateBytesToSign(byte[]... args) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            for (byte[] arg : args) {
                outputStream.write(arg);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to concatenate bytes buffer");
        }
        return outputStream.toByteArray();
    }

}
