package game;

import java.io.IOException;

/**
 * @author Kasjanoves
 */
public class TheGameApp {
    public static void main(String[] args) throws IOException {
        int port = 5555; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port: " + port);
            }
        }
        GameServer server = new GameServer();
        server.start(port);
    }
}