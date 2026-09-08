package poneytoponey;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import crypto.Crypto;
import crypto.RSA;

// Some basic hand-crafted tests without JUnit to verify correctness of implementation
// ChatGPT public free version was heavily used to write this
public class RSACryptoCheck {

	public static void main(String[] args) throws Exception, NoSuchAlgorithmException {
		System.out.println("CRYPTO MANUAL TEST");

		Crypto crypto;
		try {
			crypto = new RSA();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Stop");
		}

		// These values were generated with the help of these online tools. The
		// parameters must be the same as RSA.java to work !
		// https://www.lddgo.net/en/encrypt/rsa-sign-verify
		PublicKey publicKey = crypto.decodePublicKey("""
				-----BEGIN PUBLIC KEY-----
				MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlqesJjzAu5Zat6M8DQaS
				IWgSgQ2YF5/TlBe8/yHRj6/n5CIA/qObE7brikPi83mQAIJKcFzeY3bXFhcAy56+
				YWlqWyAE1eTMJF3Eg6IM73IeF+ykHKJI0Ywn2YAiPDKPIZ+go+MJULMu15Ly9Nzp
				lOkFUvkhiz9VSvpNRFQFtLPbQohDMGA3aNbdxaZkSkuHvTLeulqqs7TpSe3eoWfL
				OKDpWRRNdlZYz4vnMMkwL9eNqZ7ko0IQdM66l6f4oiYt0G6jwWscYSzRhbQMdIHd
				ZePXdKlv3ZGQTj64wVpPL4bIWYSxWt0O3S3uAWJkbtKgrSQQ//TNlA8huD3e2QsW
				lwIDAQAB
				-----END PUBLIC KEY-----
				""");
		PrivateKey privateKey = crypto.decodePrivateKey("""
				-----BEGIN RSA PRIVATE KEY-----
				MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCWp6wmPMC7llq3
				ozwNBpIhaBKBDZgXn9OUF7z/IdGPr+fkIgD+o5sTtuuKQ+LzeZAAgkpwXN5jdtcW
				FwDLnr5haWpbIATV5MwkXcSDogzvch4X7KQcokjRjCfZgCI8Mo8hn6Cj4wlQsy7X
				kvL03OmU6QVS+SGLP1VK+k1EVAW0s9tCiEMwYDdo1t3FpmRKS4e9Mt66WqqztOlJ
				7d6hZ8s4oOlZFE12VljPi+cwyTAv142pnuSjQhB0zrqXp/iiJi3QbqPBaxxhLNGF
				tAx0gd1l49d0qW/dkZBOPrjBWk8vhshZhLFa3Q7dLe4BYmRu0qCtJBD/9M2UDyG4
				Pd7ZCxaXAgMBAAECggEBAIsdd7yzWvGdrFVomVoZ2DSa6oNa7zfZcUGODgq1ytJZ
				B9PEhpCcuOWigqvhdh+IAC5IIb7SMtU79u9B/I0KIBjJg9lznqN4q/1kzMMUj8dH
				5HwJG54JSR6ewml4haR6t2rAAVF0o7RuUXHlEw6DM/o6lp0AyjXQHM2ohlLzF3W9
				M6aHwl37Q+7MFCLTcl1aGhAafWc3/oGCTk3NcMtFI246kh1uIoxx9LeSWHs6QOTz
				5NOAgX2EFbE7LQcBPBGgvxib8mN57HVXj6aorXCHdbWmaRCTGdQhiqquk9cB23CZ
				gk2Ye2f1G7cAw1OrvUEuu9H6I/Wgd6zty9z71YD+QKECgYEA59dEKJJdTCehOPzF
				gnfg3UHxenqdd8M8V/uYKqheXtEQONKU8yFGsJehhzyUxb4aW8FdO0HFMGDTkSgM
				IoOH5o5+arGXYC1D9gLXaCgmgr1ucliHBQcnM6wuxmWz/GUf/qXnY4JsvtOtkYOd
				BCaFObk3QADnToj/cPhRJUnl4rECgYEAplqjuWZFekCwbiOfYXG2QhZanlOspKDl
				rJPm2haZDIzvVtUEh1Od1GrvlyfkAeBvrVshfr+kOmZ5oJeJYTFZt1cCRwhN8BxS
				Y4HptWEGr3KyPUc7LvzLbvhYUKa6lqZbJqbXEmi4g+7VBOt0vF25Zko3qFuLanP8
				TvcHXnDnj8cCgYEAzRYaTyGasqFj9ztzpMRTAKcy7DvHTIhCuznvE905Dfs2tG1k
				IedP1ytrUmx+1qYiPwTAaoj13KPqw79/bJCaCZQHtG5BEG06b0d4McVCu+jC6Rfy
				6Lm5LfakshCJtT3nnaY5GEfp1qz+H3kjzuYceEGl5FJoS4rZHKAteCT03SECgYAe
				2791O9h1hhx0Qd1l2Q8jETx8FC95VnNkYQc4u2pmnpojNF6jr8oeRjvtgTPVeUme
				EcKT3X7canfzZ0jPo7TtwpEklhDHEqnh1Y6B296+mvsfTOBYpgIisqTHSTZhjbho
				4hgRTSW1K4+OWxMLgiNLK0dJzrl85MkP6NkQFseNrwKBgF+h9vO9+HzE7uCOBSF7
				3B8YfeMMkAcvVSWQw4PExVfC88fmk7dW38ItpWwKZnD4kc34Wtm+7htGu4Q5z9bj
				Tw1vwsWD7rEBXo2f7TSq3Nxc7Lir2KBKex8iPN+Rl1f8RRtAJRT0n0gUpHsPyhe7
				Q54zdoLE4SOw7gBXC5kfSEik
				-----END RSA PRIVATE KEY-----
				                """);

		String message = "super important message to sign !!!!";
		String validSignatureBase64 = "MGVpvnvv1cF/XRBX3GUZmzEHM+CKTQ3D3++9obgZ63tueyMPM8lBSxufTJlGoBrdGeYqi55qqMvWP9QRbcxiUul9Z3gQjoXrJnJNJqBHBIzKicP3QFAr/c6eQLTdBts7am4apMNWrEoIZ8xjaFjZ0Xv8vyWMoPYfimVRzAfP7fBkgTzdGj9YnMgAOEzOt2dyrW65M7cnqcyTFbNl6wWxdaBLTC3OnEkkPKvSFvh1zHmTSSH3831k4g0ny3KgMyCCW59gKq0WGhkud6d1BVDVgRXGzWHEXEa2iAzDaJovHjfflKad7dyI76RBbYpQs/uBeoXS4lUv3oz7XGwtzrwOdA==";
		String plaintext = "hello there my friends";
		String validCiphertextBase64 = "WF1k9njAqw6tmcA5pKJRKhNXLev3tc2Lfga0jLzzWZfXnfi/i9NXVCSvAutQudctCqnKKcdcahPrn+pk0q0VrE7pdMu12O/p6+cDatfrM88WHGWOWNrdBZezX4tmH5b2/f/lyAs7zKQWmF3kpxzIyN28YZqW8XGqOas1qNzAK6VRI/Ki49ujXAluVBF301J2WI9bi+WHbWeJJzrKaZK5qhmU+XM1NHA8mG0vGglIh150N8dKQuQGH+4IYATVbHCCwIA78RSzrsn+Ah4gzmiwubl3dybMYsOQblhcAjlqYTYDgmRb1d4PFVGLGCgLZdipZrLjmMxO8b66CAX3qXDx5A==";

		System.out.println("[OK] Keypair generated");

		// SIGNATURE TESTS

		byte[] content = message.getBytes(StandardCharsets.UTF_8);

		System.out.println("SIGNATURE TESTS");

		// Generate a real signature
		byte[] generatedSignature = crypto.sign(content, privateKey);
		String generatedSignatureBase64 = Base64.getEncoder().encodeToString(generatedSignature);
		System.out.println("[INFO] Generated signature (Base64):");
		System.out.println(generatedSignatureBase64);
		System.out.println(generatedSignatureBase64.equals(validSignatureBase64) ? "[OK] Signature correctly generated"
				: "[FAIL] Incorrect signature generation");

		// VALID SIGNATURE TEST

		byte[] validSignature = Base64.getDecoder().decode(validSignatureBase64);
		System.out.println(crypto.verifySignature(validSignature, content, publicKey) ? "[OK] Valid signature accepted"
				: "[FAIL] Valid signature rejected");

		// INVALID SIGNATURE TEST

		String invalidSignatureBase64 = validSignatureBase64.replaceFirst("o", "b");
		byte[] invalidSignature = Base64.getDecoder().decode(invalidSignatureBase64);
		boolean invalidSignatureResult = crypto.verifySignature(invalidSignature, content, publicKey);
		System.out.println(
				!invalidSignatureResult ? "[OK] Invalid signature rejected" : "[FAIL] Invalid signature accepted");

		System.out.println("ENCRYPTION TESTS");

		byte[] generatedCiphertext = crypto.encrypt(plaintext, publicKey);

		System.out.println("[INFO] Generated ciphertext (Base64):");
		System.out.println(Base64.getEncoder().encodeToString(generatedCiphertext));

		// VALID DECRYPTION TEST
		byte[] validCiphertext = Base64.getDecoder().decode(validCiphertextBase64);
		String decrypted = crypto.decrypt(validCiphertext, privateKey);
		System.out.println(plaintext.equals(decrypted) ? "[OK] Valid ciphertext decrypted correctly"
				: "[FAIL] Decrypted text mismatch");
		System.out.println("[INFO] Decrypted value: " + decrypted);

		// INVALID DECRYPTION TEST
		String invalidCiphertextBase64 = validCiphertextBase64.replaceFirst("a", "4");
		try {
			byte[] invalidCiphertext = Base64.getDecoder().decode(invalidCiphertextBase64);
			String decrypted2 = crypto.decrypt(invalidCiphertext, privateKey);
			System.out.println("[FAIL] Invalid ciphertext decrypted unexpectedly");
			System.out.println("[INFO] Result: " + decrypted2);
		} catch (Exception e) {
			System.out.println("[OK] Invalid ciphertext rejected");
			System.out.println("[INFO] Exception: " + e.getMessage());
		}

		System.out.println("TESTS FINISHED");
	}
}
