package poneytoponey;

public class App {

    public static final int PORT = 7000;

    public static void main(String[] args) {
        String host = "localhost";
        if (args.length > 0) {
            host = args[0];
        }
        ShellView view = new ShellView();
        view.start(host);

        // D1
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HumanIdentity identity = view.getIdentity();
            if (identity != null) {
                identity.stopWatchAcks();
            }
        }));
    }
}
