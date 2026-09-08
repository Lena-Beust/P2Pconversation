package poneytoponey;

import crypto.KeyPair;
import crypto.RSA;

public class FaultyLeaveCheck {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: FaultyLeaveCheck <directory-url> <username>");
            return;
        }

        String host = args[0];
        String username = args[1].trim();

        KeyPair keyPair = new RSA().generateKeyPair();
        FaultyDirectory directory = new FaultyDirectory(host);

        directory.join(username, keyPair);
        System.out.println("Joined as " + username);

        try {
            directory.leave(keyPair);
            System.out.println("Unexpected success: faulty leave was accepted");
        } catch (Exception e) {
            System.out.println("Expected leave failure: " + e.getMessage());
        }

        directory.setTamperLeaveSignature(false);
        directory.leave(keyPair);
        System.out.println("Cleanup leave succeeded");
    }
}
