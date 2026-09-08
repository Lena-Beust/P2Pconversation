package poneytoponey;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.*; //D2
import java.nio.file.*; //D2
import crypto.KeyPair;
import crypto.RSA;

public class HumanIdentity implements Identity {

    private static boolean debug = false;
    private String username;
    private Map<UUID, Chat> chats;
    private Directory directory;
    private final KeyPair keyPair;
    private List<View> views;
    private Registry ourLocalRegistry;
    private String IDENTITY_BIND = "identity";
    private final ScheduledExecutorService watchAcks = Executors.newSingleThreadScheduledExecutor(); // D1
    private static final int WATCH_ACK_FREQUENCY = 1; // checking every second

    private Path SAVE_PATH; // D2, emplacement ou les chats sont sauvegardés

    public HumanIdentity(String user, Directory directory) {
        this.directory = directory;
        this.username = user;
        // Generate a new keypair or take an existing pair
        if (KeyPair.aPairExists()) {
            this.keyPair = KeyPair.load();
        } else {
            this.keyPair = generateKeyPair();
            this.keyPair.persistToFile();
        }

        if (System.getProperty("java.rmi.server.hostname") == null) {
            try {
                System.setProperty("java.rmi.server.hostname", resolveRmiHostname());
            } catch (UnknownHostException | SocketException e) {
                System.err.println("Cannot detect local RMI hostname: " + e.getMessage());
            }
        }
        this.views = Collections.synchronizedList(new ArrayList<View>());
        this.chats = new ConcurrentHashMap<>();
        try {
            Files.createDirectories(Paths.get("chats")); // D2 crée dossier chats pour sauvegarde
        } catch (IOException e) {
            System.err.println("problème de création du dossier de sauvegarde pour les messages" + e.getMessage());
        }
        this.SAVE_PATH = Paths.get("chats", user + ".dat"); // D2
        loadChats(); // D2
        try {
            // Try joining the network by publishing the current object to the our local RMI
            // registry
            ourLocalRegistry = LocateRegistry.createRegistry(poneytoponey.App.PORT);
            // We have to publish this object fist before binding it to the registry
            Identity stub = (Identity) UnicastRemoteObject.exportObject(this, poneytoponey.App.PORT);
            ourLocalRegistry.bind(IDENTITY_BIND, stub);
            try {
                this.directory.join(username, keyPair);
                // Register a hook to run at shutdown to make sure we leave() the directory
                // before quitting the app when running Ctrl+c!
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    // We don't want to leave to allow force quitting the terminal to simulate a
                    // network disconnect
                    // leave();
                }));
            } catch (Exception e) {
                System.err.println(
                        "Already joined the network");

                Optional<Entry> maybeEntry = this.directory.list().stream()
                        .filter(entry -> entry.username().equals(username))
                        .findFirst();
                if (!maybeEntry.isEmpty() && maybeEntry.get().publicKey().equals(keyPair.getPublic())) {
                    this.username = maybeEntry.get().username();
                } else {
                    System.err.println("Failed to join the network :" + e.getMessage());
                    System.exit(2);
                }
            }
        } catch (AlreadyBoundException e) {
            System.err.println(e.getMessage());
        } catch (RemoteException e) {
            System.err.println(e.getMessage());
        }
    }

    public void leave() {
        System.out.println("Goodbye.");
        try {
            this.directory.removeUser(this.username, keyPair);
        } catch (Exception e) {
            System.err.println("Failed to leave sorry, but byebye: " + e.getMessage());
        }
    }

    public String getUsername() {
        return this.username;
    }

    public List<String> listParticipantsUsername() {
        // We can extract the username of all members in the network by listing entries
        // on the directory and only keeping usernames
        try {
            return this.directory.list().stream()
                    .map(entry -> entry.username())
                    .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Optional<PublicKey> getParticipantPublicKey(String username) {
        try {
            Optional<Entry> maybeEntry = this.directory.list().stream()
                    .filter(entry -> entry.username().equals(username))
                    .findFirst();
            if (maybeEntry.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(maybeEntry.get().publicKey());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Identity getRemoteIdentityFromUsername(String recipient) throws Exception {
        Optional<Entry> maybeEntry = this.directory.list().stream().filter(entry -> entry.username().equals(recipient))
                .findFirst();
        if (maybeEntry.isEmpty()) {
            throw new Exception("This participant doesn't exist in the network !");
        }
        Entry entry = maybeEntry.get();
        String distantIP = entry.ip();

        Registry remoteRegistry = LocateRegistry.getRegistry(distantIP, App.PORT);
        return (Identity) remoteRegistry.lookup(IDENTITY_BIND);
    }

    public Chat createChat(String recipient) throws RemoteException, Exception {
        Identity remote = getRemoteIdentityFromUsername(recipient);
        Chat chat = new Chat(recipient, username);
        chats.put(chat.getUuid(), chat);
        saveChat(); // D2
        remote.remoteAskForChat(this.username, chat.getUuid());
        return chat;
    }

    private Identity getRemoteIdentityFromChat(UUID chatID) throws Exception {
        String otherUsername = this.chats.get(chatID).getOtherUsername();
        return getRemoteIdentityFromUsername(otherUsername);
    }

    public void approveChat(UUID chatID) throws RemoteException, Exception {
        Chat chat = chats.get(chatID);
        if (chat != null) {
            if (chat.getCreator().equals(this.username)) {
                throw new RuntimeException("Cannot self approve chat");
            }
            chat.setApproved(true);
            Identity remote = getRemoteIdentityFromChat(chatID);
            if (remote != null) {
                remote.remoteApproveBackChat(chatID);
            }

        }
        saveChat(); // D2
    }

    public void refuseChat(UUID oldChatID) throws RemoteException, Exception {
        Chat chat = chats.get(oldChatID);
        if (chat.getApproved() == true || chat != null) {
            Identity remote = getRemoteIdentityFromChat(oldChatID);
            chats.remove(oldChatID);
            if (remote != null) {
                remote.remoteRefuseChat(oldChatID);
            }
        }
        saveChat(); // D2
    }

    public void subscribeViewForChatEvent(View view) {
        this.views.add(view);
    }

    // M1
    public void sendMessage(UUID chatID, String text, boolean important) throws RemoteException, Exception {
        Identity remote = getRemoteIdentityFromChat(chatID);
        Chat chat = chats.get(chatID);
        if (chat != null && chat.getApproved() && text != null) {
            Message m = chat.insertNewMessage(text, this.username, important);
            chat.registerPendingAck(m.getUuid());
            // saveChat(); // D2
            Optional<PublicKey> pubkey = getParticipantPublicKey(chat.getOtherUsername());
            if (pubkey.isEmpty()) {
                throw new RemoteException("No participant " + chat.getOtherUsername() + " found in the network !");
            }
            SafeMessage safeMessage = new SafeMessage(m, keyPair.getPrivate(), pubkey.get());
            if (debug) {
                System.out.println("Sending the following safeMessage");
                safeMessage.dump();
            }
            if (remote != null) {
                remote.remoteSendMessageInChat(chatID, safeMessage);
                saveChat();
            }
        }
    }

    public void closeChat(UUID chatID) throws RemoteException, Exception {
        Identity remote = getRemoteIdentityFromChat(chatID);
        if (chats.containsKey(chatID)) {
            chats.remove(chatID);
            saveChat(); // D2
        }
        if (remote != null) {
            remote.remoteCloseChat(chatID);
        }
    }

    // ----- Identity -----
    public void remoteAskForChat(String author, UUID chatID) throws RemoteException, NotBoundException {
        chats.put(chatID, new Chat(author, chatID, author)); // save the non approved chat
        saveChat(); // pas sure de le mettre ici //D2
        for (View view : views) {
            view.showChatRequest(author);
        }

        Thread timeout = new Thread(() -> {
            try {
                Thread.sleep(20_000);
                Chat chat = chats.get(chatID);
                if (chat != null && !chat.getApproved()) {
                    refuseChat(chatID);
                }
            } catch (Exception e) {
                System.err.println("Failed to auto-refuse chat request from " + author + ": " + e.getMessage());
            }
        });
        timeout.setDaemon(true);
        timeout.start();

    }

    public void remoteApproveBackChat(UUID chatID) throws RemoteException {
        Chat chat = chats.get(chatID);

        if (chat != null) {
            String otherUsername = chat.getOtherUsername();
            chats.put(chat.getUuid(), chat);
            chat.setApproved(true);
            saveChat(); // D2
            for (View view : views) {
                view.showChatApprobation(otherUsername);
            }
        } else {
            throw new RemoteException("The chat with ID " + chatID + " doesn't exist on client '" + username
                    + "' and cannot be approved. It was either closed before or never requested...");
        }
        // TODO: do we agree we should just throw a RemoteException right ?? there is
        // not chat to approve this is an error.
    }

    public void remoteRefuseChat(UUID chatID) {
        Chat chat = chats.get(chatID);

        if (chat == null || chat.getApproved() == true) {
            return; // à revoir
        }
        chats.remove(chatID); // delete the chat as cannot do anything with it !
        saveChat(); // D2
        for (View view : views) {
            view.showChatRefuse(chat.getOtherUsername());
        }

    }

    @Override
    public void remoteSendBroadcastMessage(SignedMessage signedMessage) throws RemoteException {
        Optional<PublicKey> pubkey = getParticipantPublicKey(signedMessage.getAuthor());
        if (pubkey.isEmpty()) {
            throw new RemoteException("Received broadcast without a valid participant " + signedMessage.getAuthor());
        }
        if (!signedMessage.verifySignature(pubkey.get())) {
            throw new RemoteException(
                    "Received broadcast without a valid signature from spoofer " + signedMessage.getAuthor());
        }
        for (View view : views) {
            view.showBroadcastMessage(signedMessage);
        }
    }

    public void remoteSendMessageInChat(UUID chatID, SafeMessage safeMessage) throws RemoteException {
        Chat chat = chats.get(chatID);
        if (chat == null) {
            return; // à revoir
        }

        // Verify message's signature, decrypt it and store it
        String author = chat.getOtherUsername();
        var publicKey = getParticipantPublicKey(author);
        if (publicKey.isEmpty()) {
            throw new RemoteException("The sender of the message doesn't exist in the network !");
        }
        if (debug) {
            System.out.println("Received the following safeMessage");
            safeMessage.dump();
        }
        Message msg = safeMessage.verifyAndDecrypt(publicKey.get(), keyPair.getPrivate());
        if (msg == null) {
            throw new RemoteException("Invalid message received !");
        }
        if (debug) {
            System.out.println("SafeMessage decrypted into: " + msg.getTexte());
        }
        if (!msg.getAuthor().equals(author)) {
            throw new RemoteException("Invalid author field for this chat !");
        }
        chat.insertNewReceivedMessage(msg);

        try {
            Identity remote = getRemoteIdentityFromChat(chatID);
            if (remote != null) {
                remote.remoteAcknowledgeMessage(chatID, msg.getUuid());
            }
        } catch (Exception e) {
            System.err.println("[ACK] Impossible d'envoyer l'ACK : " + e.getMessage());
        }

        saveChat(); // D2

        for (View view : views) {
            view.showChatMessage(msg);
        }
    }

    public void remoteCloseChat(UUID chatID) {
        Chat chat = chats.get(chatID);

        if (chat == null) {
            return; // à revoir
        }

        for (View view : views) {
            view.showChatClose(chat.getOtherUsername());
        }

        chats.remove(chatID);
        chat.setApproved(false);
    }

    // ------ getters/setters for chats (to use it in shellview ----
    public Map<UUID, Chat> getChats() {
        return this.chats;
    }

    public void addToChats(UUID uuid, Chat chat) {
        this.chats.put(uuid, chat);
    }

    public void removeTOChats(UUID uuid) {
        this.chats.remove(uuid);
    }

    // -----pour trouver le uuid du récipient : chercher dans la liste de tous les
    // chats que l'on en vérifiant la condition chat.username == l'username
    // recherché-----
    public UUID findUuidByUsername(String recipientUsername) {
        if (recipientUsername == null) {
            return null;
        }
        for (Chat chat : this.chats.values()) {
            if (chat.getOtherUsername().equals(recipientUsername)) {
                return chat.getUuid();
            }
        }
        return null;
    }

    private static String resolveRmiHostname() throws SocketException, UnknownHostException {
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }

            var addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address.getHostAddress();
                }
            }
        }

        return InetAddress.getLocalHost().getHostAddress();
    }

    // D1
    @Override
    public void remoteAcknowledgeMessage(UUID ChatId, UUID messageId) throws RemoteException {
        Chat localChat = chats.get(ChatId);
        if (localChat == null)
            return;
        localChat.receiveAck(messageId);
    }

    // D1
    public void startWatchAcks() {
        watchAcks.scheduleAtFixedRate(() -> {
            for (Chat chat : chats.values()) {
                if (chat.getApproved() && chat.hasTimedOutAck()) {
                    autoDisconnect(chat);
                }
            }
        }, WATCH_ACK_FREQUENCY, WATCH_ACK_FREQUENCY, TimeUnit.SECONDS);
    }

    // D1
    private void autoDisconnect(Chat chat) {
        chats.remove(chat.getUuid());
        for (View view : views) {
            view.showChatClose(chat.getOtherUsername());
        }
        try {
            // directory.removeUser(chat.getOtherUsername(), keyPair);
        } catch (Exception e) {
            System.err.println(
                    "[WATCHDOG] Impossible de désinscrire " + chat.getOtherUsername() + " : " + e.getMessage());
        }
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    private static KeyPair generateKeyPair() {
        try {
            return new RSA().generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate identity key pair", e);
        }
    }

    // D1
    public void stopWatchAcks() {
        watchAcks.shutdown();
        try {
            if (!watchAcks.awaitTermination(3, TimeUnit.SECONDS)) {
                watchAcks.shutdownNow();
            }
        } catch (InterruptedException e) {
            watchAcks.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void saveChat() { // D2
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(SAVE_PATH))) {
            out.writeObject(chats);
        } catch (IOException e) {
            System.err.println("Can't save chats : " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked") // évite problème avec les maps pas serializables ?
    public synchronized void loadChats() { // D2
        if (Files.exists(SAVE_PATH)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(SAVE_PATH))) {
                this.chats = (Map<UUID, Chat>) in.readObject();
                // To avoid issue of null values for pendingAcks: Cannot invoke
                // "java.util.Map.put(Object, Object)" because "this.pendingAcks" is null
                for (Chat chat : chats.values()) {
                    chat.resetAcks();
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Can't load chats : " + e.getMessage());
            }
        } else {
            return;
        }
    }

    public void broadcast(String text) throws RemoteException, Exception { // M2
        List<String> users = listParticipantsUsername();
        for (String user : users) {
            if (user.equals(this.username)) { // on se l'envoie pas à soi même
                continue;
            }
            try {
                Identity remote = getRemoteIdentityFromUsername(user);
                if (remote != null) {
                    SignedMessage signedMessage = new SignedMessage(text, System.currentTimeMillis(), this.username,
                            debug, this.keyPair.getPrivate());
                    remote.remoteSendBroadcastMessage(signedMessage);
                }
                System.out.println("Successfully sent broadcast to " + user + "\n");
            } catch (Exception e) {
                System.err
                        .println("Can't send broadcast message to " + user + " (probably offline).");
            }
        }
    }
}
