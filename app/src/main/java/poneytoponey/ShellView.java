package poneytoponey;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

public class ShellView implements View {

    private UUID currentChat;
    private String currentChatRecipient;
    private boolean joinedNetwork;
    private HumanIdentity identity;
    private Scanner scanner = new Scanner(System.in);
    private String directoryHost;

    // TODO: a refactor is needed here to avoid having 2 list of Chat !
    // It seems we need to have easy access to recipient -> Chat here, but easy
    // access of Uuid -> Chat in HumanIdentity.
    // Maybe we should create getters/setter on HumanIdentity to keep the source of
    // truth over there
    // and create a mapping recipient -> Uuid here for ease of access and an
    // O(log(N)) complexity to access a chat.
    // This would also delete this attribute below
    // private Map<UUID, Chat> chats = new HashMap<>();
    private void join(String username) {
        if (username == null || username.trim().isEmpty()) {
            System.out.println("Username cannot be empty.");
            return;
        }

        if (joinedNetwork) {
            System.out.println("Already joined as " + identity.getUsername() + ".");
            return;
        }

        this.identity = new HumanIdentity(username.trim(), new Directory(directoryHost));
        this.identity.subscribeViewForChatEvent(this);
        this.joinedNetwork = true;
        System.out.println("Joined network as " + username.trim() + ".");
    }

    public void showHelp() {
        System.out.println("Available commands:"
                + "\n  join <username>       - join the network as a user"
                + "\n  list                  - list known members of the network"
                + "\n  chat <recipient>      - create or switch to a chat"
                + "\n  chats                 - list chats"
                + "\n  switch <recipient>    - switch to an existing chat"
                + "\n  send <message>        - send a message to the active chat"
                + "\n  send! <message>       - send an important message to a chat" // M1
                + "\n  broadcast <message>   - send a message to all users in the network" // M2
                + "\n  history               - show chat history of current chat"
                + "\n  close <recipient>     - close a chat with a recipient"
                + "\n  refuse <recipient>    - refuse a chat request or discard a chat"
                + "\n  approve <recipient    - approve a chat request"
                + "\n  status                - show current chat status"
                + "\n  help                  - show this help text"
                + "\n  exit | quit           - leave the shell");
    }

    private void createSwitchChat(String recipient) {
        if (recipient == null || recipient.trim().isEmpty()) {
            System.out.println("Recipient cannot be empty.");
            return;
        }

        recipient = recipient.trim();
        Map<UUID, Chat> chats = this.identity.getChats();
        UUID uuid = this.identity.findUuidByUsername(recipient);

        if (uuid == null) {
            try {
                Chat chat = identity.createChat(recipient);
                this.currentChat = chat.getUuid();
            } catch (Exception e) {
                System.out.println("Unable to create chat with " + recipient + ": " + e.getMessage());
                return;
            }
            // this.identity.chats.put(recipient, chat);
            System.out.println("Created chat with " + recipient + ".");
        } else {
            Chat chat = chats.get(uuid);
            this.currentChat = chat.getUuid();
            System.out.println("Switched to existing chat with " + recipient + ".");
        }

        this.currentChatRecipient = recipient;
    }

    private void listChats() {
        identity.getChats().values().stream().forEach(chat -> {
            System.out.println("- " + chat.getOtherUsername() + ": "
                    + (chat.getApproved() ? chat.getMessages().size() + " msgs" : "not approved"));
        });
    }

    private void closeChat(String recipient) {
        if (recipient == null || recipient.trim().isEmpty()) {
            System.out.println("Recipient cannot be empty.");
            return;
        }

        recipient = recipient.trim();
        // Chat chat = chats.remove(recipient); //normalement il n'y a plus besoin de ca
        // ? comme il est sensé déjà être closed

        // TODO : Actually close the chat, waiting for imple
        // Block sychronised ?
        try {

            identity.closeChat(identity.findUuidByUsername(recipient));
        } catch (Exception e) {
            System.out.println("No chat found with " + recipient + ":" + e.getMessage());
            return;
        }

        if (recipient.equals(currentChatRecipient)) {
            currentChat = null;
            currentChatRecipient = null;
        }

        System.out.println("Closed chat with " + recipient + ".");
    }

    private void approveChat(String recipient) {
        recipient = recipient.trim();
        if (recipient == null || recipient.isEmpty()) {
            System.out.println("Recipient cannot be empty.");
            return;
        }
        UUID uuid = identity.findUuidByUsername(recipient);
        if (uuid != null) {
            Chat existingChat = identity.getChats().get(uuid);
            try {
                identity.approveChat(identity.findUuidByUsername(recipient));
            } catch (Exception e) {
                System.out.println("Failed to approve chat with " + recipient + ": " + e.getMessage());
                return;
            }
            existingChat.setApproved(true);

            currentChat = existingChat.getUuid();
            currentChatRecipient = existingChat.getOtherUsername();
            System.out.println("Approved chat with " + recipient + ". Start chatting now !");
        } else {
            System.out.println("No requested chat with " + recipient + ", you cannot approve an non existant chat.");
        }
    }

    private void refuseChat(String recipient) {
        if (recipient == null || recipient.trim().isEmpty()) {
            System.out.println("Recipient cannot be empty.");
            return;
        }

        recipient = recipient.trim();
        UUID uuid = this.identity.findUuidByUsername(recipient);
        if (uuid != null) {
            Chat maybeChat = this.identity.getChats().get(uuid);
            UUID oldChatID = maybeChat.getUuid();
            // chats.remove(recipient);

            try {
                identity.refuseChat(oldChatID);
            } catch (Exception e) {
                System.out.println("Failed to refuse chat with " + recipient
                        + e.getMessage());
                return;
            }

            if (recipient.equals(currentChatRecipient)) {
                currentChat = null;
                currentChatRecipient = null;
            }
            System.out.println("Refused chat with " + recipient + ".");
        } else {
            System.out.println("No chat request or chat found for " + recipient + ".");
        }
    }

    private void sendMessage(String text, boolean important) {
        if (currentChat == null || currentChatRecipient == null) {
            System.out.println("No active chat selected. Use chat <recipient> first.");
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            System.out.println("Cannot send an empty message.");
            return;
        }

        UUID uuid = this.identity.findUuidByUsername(currentChatRecipient);
        if (uuid == null) {
            System.out.println("Current chat is no longer available.");
            currentChat = null;
            currentChatRecipient = null;
            return;
        }
        Chat chat = this.identity.getChats().get(uuid);

        if (!chat.getApproved()) {
            System.out.println("Chat with " + chat.getOtherUsername()
                    + " was not yet approved. You cannot send a message for now.");
            return;
        }
        try {
            identity.sendMessage(currentChat, text.trim(), important);
            System.out.println("Sent" + (important ? " important" : "") + " message to " + currentChatRecipient + " at "
                    + formatTimestamp(System.currentTimeMillis()) + ": " + text.trim());
        } catch (Exception e) {
            System.out.println("Cannot send message : " + e.getMessage());
        }
    }

    private void broadcast(String text) { // M2
        if (text == null || text.trim().isEmpty()) {
            System.out.println("Broadcast message cannot be empty");
            return;
        }
        try {
            identity.broadcast(text.trim());
            System.out.println("Sent broadcast : " + text.trim());
        } catch (Exception e) {
            System.out.println("Broadcast failed : " + e.getMessage());
        }
    }

    private void listParticipants() {
        List<String> participants = identity.listParticipantsUsername();
        if (participants.isEmpty()) {
            System.out.println("No participants found.");
            return;
        }

        System.out.println("Known participants:");
        for (String participant : participants) {
            System.out.println("  - " + participant);
        }
    }

    private void showPrompt() {
        System.out.print("P2P> ");
    }

    private void waitWithShellPrompt() {
        while (true) {
            showPrompt();
            try {
                String line = scanner.nextLine();
                parseCommand(line);
            } catch (NoSuchElementException e) {
                // We probably have closed the stdin stream (probably with ctrl+d)
                exit();
            }
        }
    }

    private void parseCommand(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        String[] tokens = line.trim().split(" ", 2);
        String command = tokens[0].toLowerCase();
        String argument = tokens.length > 1 ? tokens[1].trim() : null;

        if (!command.equals("join") && !joinedNetwork) {
            System.out.println("You must join the network first.");
            return;
        }

        switch (command) {
            case "join" ->
                join(argument);
            case "list" ->
                listParticipants();
            case "chat" ->
                createSwitchChat(argument);
            case "chats" ->
                listChats();
            case "switch" -> {
                if (argument == null || argument.isEmpty()) {
                    System.out.println("Usage: switch <recipient>");
                } else if (!this.identity.getChats().containsKey(this.identity.findUuidByUsername(argument))) {
                    System.out.println("No existing chat with " + argument + ".");
                } else {
                    currentChatRecipient = argument;
                    currentChat = this.identity.findUuidByUsername(currentChatRecipient);
                    System.out.println("Switched to chat with " + currentChatRecipient + ".");
                }
            }
            case "send" ->
                sendMessage(argument, false);
            case "send!" ->
                sendMessage(argument, true); // M1
            case "broadcast" ->
                broadcast(argument); // M2
            case "history" ->
                showHistory();
            case "close" ->
                closeChat(argument);
            case "approve" ->
                approveChat(argument);
            case "refuse" ->
                refuseChat(argument);
            case "status" -> {
                if (!joinedNetwork) {
                    System.out.println("Not joined.");
                } else if (currentChatRecipient == null) {
                    System.out.println("Joined as " + identity.getUsername() + ". No active chat.");
                } else {
                    System.out.println(
                            "Joined as " + identity.getUsername() + ". Active chat with " + currentChatRecipient + ".");
                }
            }
            case "help" ->
                showHelp();
            case "exit", "quit" ->
                exit();
            default ->
                System.out.println("Unknown command: " + command + ". Type help for available commands.");
        }
    }

    private void exit() {
        if (identity != null) {
            identity.leave();
        }
        System.out.println();
        System.exit(0);
    }

    private String formatTimestamp(long millis) {
        Instant instant = Instant.ofEpochMilli(millis);
        ZoneId localZone = ZoneId.systemDefault();
        ZonedDateTime localTime = instant.atZone(localZone);
        return localTime.format(
                DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private void showMessage(Message msg) {
        System.out.println(formatTimestamp(msg.getSenderTimestamp()) + " - " + msg.getAuthor() + ": "
                + (msg.getIsImportant() ? "[IMPORTANT] " : "") + msg.getTexte());
    }

    @Override
    public void start(String directoryHost) {
        this.directoryHost = directoryHost;
        System.out.println("Welcome to the PoneyToPoney peer-to-peer system !");
        System.out.print("Please choose a username to join the network: ");
        try {
            String username = scanner.nextLine();
            if (!username.trim().isEmpty()) {
                join(username);
            }
        } catch (NoSuchElementException e) {
            // We probably have closed the stdin stream (probably with ctrl+d)
            exit();
        }
        identity.startWatchAcks();
        showHelp();
        waitWithShellPrompt();
    }

    @Override
    public void showChatRequest(String from) {
        System.out.println("Incoming chat request from " + from + ".");
        showPrompt();
    }

    @Override
    public void showChatClose(String from) {
        System.out.println("Chat closed by " + from + ".");
        showPrompt();
    }

    @Override
    public void showChatApprobation(String from) {
        System.out.println("Chat request approved by " + from + ".");
        showPrompt();
    }

    @Override
    public void showChatRefuse(String from) {
        System.out.println("Chat refused by " + from + ".");
        showPrompt();
    }

    @Override
    public void showChatMessage(Message msg) {
        if (currentChatRecipient.equals(msg.getAuthor())) {
            showMessage(msg);
        } else { // M1
            String toPrint = msg.getIsImportant()
                    ? "New important message from " + msg.getAuthor() + ": " + msg.getTexte()
                    : "New message available from " + msg.getAuthor() + ".";
            System.out.println(toPrint);
        }
        showPrompt();
    }

    private void showHistory() {
        if (currentChatRecipient == null) {
            System.out.println("No active chat. Use chat <recipient> to select a chat.");
        } else {
            UUID uuid = this.identity.findUuidByUsername(currentChatRecipient);
            if (uuid == null) {
                System.out.println("Current chat is no longer available.");
            } else {
                Chat chat = this.identity.getChats().get(uuid);
                List<Message> messages = chat.getMessages();
                if (messages.isEmpty()) {
                    System.out.println("No messages in chat with " + currentChatRecipient + ".");
                } else {
                    System.out.println("History for chat with " + currentChatRecipient + ":");
                    for (Message message : messages) {
                        showMessage(message);
                    }
                }
            }
        }
    }

    // D1
    public HumanIdentity getIdentity() {
        return this.identity;
    }

    @Override
    public void showBroadcastMessage(Message msg) {
        System.out.println("Broadcast from " + msg.getAuthor() + ": ");
        showMessage(msg);
        showPrompt();
    }
}
