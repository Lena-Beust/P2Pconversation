package poneytoponey;

import java.util.Base64;

import crypto.KeyPair;

public class FaultyDirectory extends Directory {

    private boolean tamperRegisterSignature;
    private boolean tamperLeaveSignature = true;

    public FaultyDirectory(String host) {
        super(host);
    }

    public void setTamperRegisterSignature(boolean tamperRegisterSignature) {
        this.tamperRegisterSignature = tamperRegisterSignature;
    }

    public void setTamperLeaveSignature(boolean tamperLeaveSignature) {
        this.tamperLeaveSignature = tamperLeaveSignature;
    }

    @Override
    protected String buildRegisterSignature(String signedContent, KeyPair keyPair) {
        String signature = super.buildRegisterSignature(signedContent, keyPair);
        return tamperRegisterSignature ? corruptSignature(signature) : signature;
    }

    @Override
    protected String buildLeaveSignature(String signedContent, KeyPair keyPair) {
        String signature = super.buildLeaveSignature(signedContent, keyPair);
        return tamperLeaveSignature ? corruptSignature(signature) : signature;
    }

    private String corruptSignature(String signatureBase64) {
        byte[] signature = Base64.getDecoder().decode(signatureBase64);
        signature[0] ^= 0x01;
        return Base64.getEncoder().encodeToString(signature);
    }
}
