package poneytoponey;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface Identity extends Remote {

    public void remoteAskForChat(String author, UUID chatID) throws RemoteException, NotBoundException;

    public void remoteApproveBackChat(UUID chatID) throws RemoteException;

    public void remoteRefuseChat(UUID chatID) throws RemoteException;

    public void remoteSendMessageInChat(UUID chatID, SafeMessage safeMessage) throws RemoteException; // M1

    public void remoteSendBroadcastMessage(SignedMessage signedMessage) throws RemoteException; // M2

    public void remoteCloseChat(UUID chatID) throws RemoteException;

    public void remoteAcknowledgeMessage(UUID chatId, UUID messageId) throws RemoteException; // D1
}
