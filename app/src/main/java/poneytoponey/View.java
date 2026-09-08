package poneytoponey;

public interface View {

    void start(String directoryHost);

    void showChatRequest(String from);

    void showChatClose(String from);

    void showChatRefuse(String from);

    void showChatApprobation(String from);

    void showChatMessage(Message msg);

    void showBroadcastMessage(Message msg);

    // D1
    HumanIdentity getIdentity();
}
