package game;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Kasjanoves
 */
@Log
public class GameServer {

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private static final BlockingQueue<PlayerConnection> availableClientConnections = new LinkedBlockingQueue<>();

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        log.info("server started on port " + port);
        while (true) {
            PlayerConnection connectionHandler = new PlayerConnection(serverSocket.accept());
            executor.execute(connectionHandler);
        }
    }

    public void stop() throws IOException {
        serverSocket.close();
    }

    //threaded player connection
    static private class PlayerConnection extends Thread {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        @Getter
        private String clientName;
        @Getter
        @Setter
        private GameSession gameSession;
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();

        public PlayerConnection(Socket socket) {
            log.info("new player connected");
            this.clientSocket = socket;
        }

        public void run() {

            init();

            requestName();

            // wait for another player to join or will be taken by another player
            waitForAnotherPlayer();

            initGameSessionIfNeeded();

            // main game loop
            while (gameSession != null && !gameSession.isOver) {
                gameSession.play(this);
            }

            closeResources();
        }

        private void closeResources() {
            try {
                in.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            out.close();
            try {
                clientSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // if there is no game session, create a new one and take another player
        private void initGameSessionIfNeeded() {
            if (gameSession == null) {
                availableClientConnections.remove(this);
                PlayerConnection otherPlayer;
                try {
                    otherPlayer = availableClientConnections.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                log.info(String.format("%s takes %s player", clientName, otherPlayer.getClientName()));
                sendMessage(String.format("gamer with name %s joined, let's start the game!", otherPlayer.getClientName()));
                new GameSession(this, otherPlayer).start();
                log.info(String.format("new game session started by %s", clientName));
            }
        }

        // wait for another player to join
        private void waitForAnotherPlayer() {
            lock.lock();
            try {
                while (gameSession == null && availableClientConnections.size() < 2) {
                    sendMessage("waiting for another player to join...");
                    log.info(String.format("player %s waiting for another player to join...", clientName));
                    condition.await();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }

        // ask for a name and put the player in the queue
        private void requestName() {
            sendMessage("hello guest");
            clientName = getInputLine("please enter your name:");
            try {
                availableClientConnections.put(this);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void init() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public String getInputLine(String message) {
            String inputLine = null;
            while (inputLine == null) {
                out.println(message);
                try {
                    inputLine = in.readLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return inputLine;
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void notifyPlayer() {
            lock.lock();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    //replayable game session
    static private class GameSession {
        private final PlayerConnection player1;
        private final PlayerConnection player2;
        @Getter
        private boolean isOver;
        private GameEntity player1Move;
        private GameEntity player2Move;
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        @Getter
        private PlayerConnection winner;
        private String player1Answer;
        private String player2Answer;

        public GameSession(PlayerConnection player1, PlayerConnection player2) {
            this.player1 = player1;
            this.player2 = player2;
        }

        // start the game session and notify the player
        public void start() {
            player1.setGameSession(this);
            player2.setGameSession(this);
            player2.sendMessage("you are in the game with " + player1.getClientName());
            player2.notifyPlayer();
        }

        public void play(PlayerConnection player) {

            log.info(String.format("player %s makes move", player.getClientName()));
            //ask for a move
            String inputLine;
            while (true) {
                inputLine = player.getInputLine("make your move: ");
                if (GameEntity.getAvailableNames().contains(inputLine.toLowerCase())) {
                    break;
                }
                player.sendMessage("here should be one of the following: " + GameEntity.getAvailableNames() + ", try again");
            }
            GameEntity move = GameEntity.valueOf(inputLine.toUpperCase());
            setPlayerMove(player, move);

            PlayerConnection otherPlayer = getOtherPlayer(player);

            //waiting for the other player to make a move
            player.sendMessage("waiting for the other player to make a move...");
            log.info(String.format("player %s waiting for the other player to make a move...", player.getClientName()));
            try {
                barrier.await();
            } catch (BrokenBarrierException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            //define the winner
            if (winner == null) {
                GameEntity otherPlayersMove = getOtherPlayersMove(player);
                winner = getTheWinner(player, move, otherPlayersMove, otherPlayer);
            }

            //ask if they want to play again
            barrier.reset();
            String playersAnswer = player.getInputLine("do you want to play again? (yes/no)");
            setAnswer(player, playersAnswer);
            log.info(String.format("player %s waiting for the other player to answer...", player.getClientName()));
            player.sendMessage("waiting for the other player to answer...");
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            if ("no".equalsIgnoreCase(player1Answer) || "no".equalsIgnoreCase(player2Answer)) {
                log.info(String.format("player's %s answer is %s", player.getClientName(), playersAnswer));
                isOver = true;
                player1.sendMessage("bye");
                player2.sendMessage("bye");
                return;
            }

            //reset the game session
            barrier.reset();
            player1Move = null;
            player2Move = null;
            player1Answer = null;
            player2Answer = null;
            winner = null;
        }

        private void setAnswer(PlayerConnection player, String inputLine) {
            if (player == player1) {
                player1Answer = inputLine;
            } else {
                player2Answer = inputLine;
            }
        }

        //determine the winner relies on the game logic
        synchronized private static PlayerConnection getTheWinner(PlayerConnection player, GameEntity move, GameEntity otherPlayersMove,
                                                                  PlayerConnection otherPlayer) {
            int result = move.compare(otherPlayersMove);
            if (result == 0) {
                player.sendMessage("draw");
                return null;
            }
            if (result > 0) {
                player.sendMessage("you win");
                return player;
            }
            player.sendMessage("you lose");
            return otherPlayer;
        }

        private PlayerConnection getOtherPlayer(PlayerConnection player) {
            if (player == player1) {
                return player2;
            } else {
                return player1;
            }
        }

        private GameEntity getOtherPlayersMove(PlayerConnection player) {
            if (player == player1) {
                return player2Move;
            } else {
                return player1Move;
            }
        }

        private void setPlayerMove(PlayerConnection player, GameEntity move) {
            if (player == player1) {
                player1Move = move;
            } else {
                player2Move = move;
            }
        }
    }
}