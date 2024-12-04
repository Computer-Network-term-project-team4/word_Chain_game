package word_chain.server;

import word_chain.gameLogic.GameScore;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server_test Class
 * - Acts as the server for the word-chain (끝말잇기) game.
 * - Manages client connections, game progression, turn timing, and score calculations.
 * - Handles broadcasting messages to clients and maintaining the game state.
 */
public class Server_test {
    // Server configuration constants
    private static final int PORT = 12345;              // Server listening port
    private static final int MAX_PLAYERS = 4;           // Maximum number of players allowed
    private static final int MAX_TURNS = 5;             // Maximum number of turns per player
    private static int TURN_TIME_LIMIT = 5;      // Time limit (in seconds) for each turn
    
        // List to keep track of connected clients
        private final List<ClientHandler_test> clients = Collections.synchronizedList(new ArrayList<>());
        // Instance of GameScore to manage game scoring logic
        private final GameScore gameScore = new GameScore();
        // ScheduledExecutorService to handle turn timing and periodic tasks
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
        // Server state variables
        private boolean isRunning = true;                   // Indicates if the server is running
        private boolean gameStarted = false;                // Indicates if the game has started
        private int currentPlayerIndex = 0;                 // Index of the current player in the clients list
        private int remainingTurns = MAX_TURNS;             // Remaining number of turns in the game
        private AtomicInteger remainingTime = new AtomicInteger(TURN_TIME_LIMIT); // Time left for the current turn
        private String lastWord = null;                      // The last valid word played in the game
        private char startingLetter;                         // The starting letter for the first word
        private ScheduledFuture<?> timeUpdateTask;           // Scheduled task for updating turn time
    
        /**
         * Entry point of the server application.
         * Initializes and starts the server.
         *
         * @param args Command-line arguments (not used).
         */
        public static void main(String[] args) {
            new Server_test().startServer();
        }
    
        /**
         * Broadcasts a message to all connected clients.
         * This method is synchronized to ensure thread safety when accessing the clients list.
         *
         * @param message The message to broadcast.
         */
        public synchronized void broadcast(String message) {
            synchronized (clients) {
                for (ClientHandler_test client : clients) {
                    client.sendMessage(message);
                }
            }
        }
    
        /**
         * Broadcasts the list of current participants to all clients.
         * Formats the participant list as a comma-separated string.
         */
        public synchronized void broadcastParticipants() {
            StringBuilder participantsMessage = new StringBuilder("PARTICIPANTS_UPDATE|");
            synchronized (clients) {
                int count = 0;
                for (ClientHandler_test client : clients) {
                    String nickname = client.getNickname();
                    if (nickname != null && !nickname.trim().isEmpty()) {
                        participantsMessage.append(nickname).append(",");
                        count++;
                    }
                }
                if (count > 0) {
                    participantsMessage.setLength(participantsMessage.length() - 1); // Remove the trailing comma
                } else {
                    participantsMessage.append("-");
                }
                broadcast(participantsMessage.toString());
            }
        }
    
        /**
         * Removes a client from the server's client list.
         * Notifies other clients about the disconnection and handles turn progression if necessary.
         *
         * @param client The client to remove.
         */
        public synchronized void removeClient(ClientHandler_test client) {
            synchronized (clients) {
                int removedIndex = clients.indexOf(client);
                clients.remove(client);
                client.sendMessage("당신은 게임에서 나갔습니다."); // "You have left the game."
                broadcast(client.getNickname() + " 님이 나갔습니다."); // "[Nickname] has left the game."
                System.out.println("클라이언트 연결 종료: 닉네임 = " + client.getNickname()); // "Client disconnected: Nickname = [Nickname]"
    
                // If no clients are connected, end the game
                if (clients.isEmpty()) {
                    endGame();
                    return;
                }
    
                // If the removed client was the current player, adjust the currentPlayerIndex
                if (removedIndex == currentPlayerIndex) {
                    currentPlayerIndex %= clients.size();
                    nextTurn();
                } else if (removedIndex < currentPlayerIndex) {
                    currentPlayerIndex--;
                }
    
                sendGameStatus();
            }
        }
    
        /**
         * Starts the game if it hasn't already started.
         * Initiates a 5-second countdown before beginning the first turn.
         */
        public synchronized void startGame() {
            if (gameStarted) return; // Prevent multiple game starts
            gameStarted = true;
    
            // Start a new thread for the 5-second countdown
            new Thread(() -> {
                for (int countdown = 5; countdown > 0; countdown--) {
                    broadcast("게임이 " + countdown + "초 후에 시작됩니다!"); // "The game will start in [countdown] seconds!"
                    try {
                        Thread.sleep(1000); // Wait for 1 second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("카운트다운 중 인터럽트 발생: " + e.getMessage()); // "Interrupted during countdown: [Error Message]"
                    }
                }
                // Randomly select a starting letter for the first word
                startingLetter = (char) ('a' + new Random().nextInt(26));
                broadcast("첫 번째 글자는 '" + startingLetter + "'로 시작해야 합니다."); // "The first word must start with '" + startingLetter + "'."
                nextTurn(); // Proceed to the first turn
            }).start();
        }
    
        /**
         * Checks if a given nickname is already in use by another client.
         *
         * @param nickname The nickname to check.
         * @return True if the nickname is duplicate, false otherwise.
         */
        public synchronized boolean isNicknameDuplicate(String nickname) {
            for (ClientHandler_test client : clients) {
                if (nickname.equalsIgnoreCase(client.getNickname())) {
                    return true;
                }
            }
            return false;
        }
    
        /**
         * Advances the game to the next player's turn.
         * Handles game termination if maximum turns are reached.
         */
        private synchronized void nextTurn() {
            // Cancel any existing time update task for the previous turn
            if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
                timeUpdateTask.cancel(true);
            }
    
            // If no turns remain, end the game
            if (remainingTurns == 0) {
                endGame();
                return;
            }
        synchronized (clients) {
            if (clients.isEmpty()) {
                broadcast("모든 플레이어가 나갔습니다. 게임을 종료합니다."); // "All players have left. Ending the game."
                endGame();
                return;
            }

            remainingTime.set(TURN_TIME_LIMIT* remainingTurns+5); // Reset the turn time
            ClientHandler_test currentPlayer = clients.get(currentPlayerIndex); // Get the current player

            broadcast("현재 차례: " + currentPlayer.getNickname()); // "Current turn: [Nickname]"

            if (lastWord != null) {
                broadcast("다음 단어는 '" + lastWord.charAt(lastWord.length() - 1) + "'로 시작해야 합니다."); // "The next word must start with '" + lastLetter + "'."
            } else {
                broadcast("첫 번째 글자는 '" + startingLetter + "'로 시작해야 합니다."); // "The first word must start with '" + startingLetter + "'."
            }

            currentPlayer.sendMessage("당신의 차례입니다! 단어를 입력하세요."); // "It's your turn! Please enter a word."
            sendGameStatus(); // Broadcast the current game status
            startTimerForPlayer(currentPlayer); // Start the turn timer for the current player
        }
    }

    /**
     * Starts a timer for the current player's turn.
     * Decrements the remaining time every second and handles turn timeout.
     *
     * @param currentPlayer The client whose turn it is.
     */
    private void startTimerForPlayer(ClientHandler_test currentPlayer) {
        // Schedule a task that runs every second to update the remaining time
        timeUpdateTask = scheduler.scheduleAtFixedRate(() -> {
            synchronized (Server_test.this) {
                int timeLeft = remainingTime.decrementAndGet(); // Decrement the remaining time
                if (timeLeft >= 0) {
                    sendGameStatus(); // Update clients with the new time
                }
                if (timeLeft <= 0) {
                    // If time has run out, notify all clients and move to the next player
                    broadcast(currentPlayer.getNickname() + " 님이 시간을 초과했습니다! 다음 차례로 넘어갑니다."); // "[Nickname] has run out of time! Moving to the next turn."
                    if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
                        timeUpdateTask.cancel(true); // Cancel the current timer
                    }
                    moveToNextPlayer(); // Proceed to the next player
                }
            }
        }, 1, 1, TimeUnit.SECONDS); // Initial delay of 1 second, then run every 1 second
    }

    /**
     * Broadcasts the current game status, including remaining time and turns.
     */
    private synchronized void sendGameStatus() {
        String statusMessage = "STATUS_UPDATE|TIME:" + remainingTime.get() + "|TURNS:" + remainingTurns;
        broadcast(statusMessage);
    }

    /**
     * Moves the game to the next player in the list.
     * Decrements remaining turns if a full cycle is completed.
     */
    private synchronized void moveToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size(); // Move to the next player
        if (currentPlayerIndex == 0) { // If back to the first player
            remainingTurns--; // Decrement the remaining turns
            sendGameStatus(); // Update game status
        }
        nextTurn(); // Proceed to the next turn
    }

    /**
     * Processes a word submitted by a player.
     * Validates the word, updates scores, and advances the game.
     *
     * @param word   The word submitted by the player.
     * @param player The client who submitted the word.
     */
    public synchronized void processWord(String word, ClientHandler_test player) {
        synchronized (clients) {
            // Check if it's the player's turn
            if (clients.indexOf(player) != currentPlayerIndex) {
                player.sendMessage("현재 당신의 차례가 아닙니다."); // "It's not your turn."
                return;
            }

            // If the player wants to end the game
            if (word.equalsIgnoreCase("end game")) {
                endGame();
                return;
            }

            // Cancel the existing timer as the player has submitted a word
            if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
                timeUpdateTask.cancel(true);
            }

            remainingTime.set(TURN_TIME_LIMIT); // Reset the turn time

            // Determine how to process the word based on whether it's the first word or a subsequent word
            String response = (lastWord == null)
                    ? gameScore.processFirstWord(word, startingLetter) // Process the first word
                    : gameScore.processNextWord(word, lastWord);      // Process a subsequent word

            // If the word is valid, update the lastWord and the player's score
            if (response.startsWith("유효한 단어")) { // "Valid word"
                lastWord = word; // Update the last word played
                int points = Integer.parseInt(response.split(": ")[1]); // Extract points from the response
                player.addScore(points); // Add points to the player's score
                response += ".누적 점수: " + player.getScore(); // Append the updated score to the response
            }

            // Log the response on the server console
            System.out.println(response);
            // Send the response back to the player
            player.sendMessage(response);
            // Broadcast the word to all clients
            broadcast("\n" + player.getNickname() + ": " + word);
            // Update the game status
            sendGameStatus();
            // Move to the next player's turn
            moveToNextPlayer();
        }
    }

    /**
     * Updates the client states based on the readiness of all players.
     * Starts the game automatically if all players are ready.
     */
    public synchronized void updateClientStates() {
        List<String> nicknames = new ArrayList<>();
        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                nicknames.add(client.getNickname());
            }
        }

        System.out.println("현재 연결된 클라이언트 닉네임 목록: " + nicknames); // "Current connected client nicknames: [nicknames]"

        // Count the number of clients with valid nicknames
        long validCount = nicknames.stream().filter(n -> n != null && !n.trim().isEmpty()).count();
        boolean allReady = validCount == MAX_PLAYERS; // Check if all maximum players have joined

        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.sendMessage(allReady ? "게임에 참여 중입니다!" : "대기 중입니다."); // "Participating in the game!" or "Waiting."
            }
        }

        // If all players are ready and the game hasn't started yet, start the game
        if (allReady && !gameStarted) {
            startGame();
        }
    }

    /**
     * Starts the server, listens for incoming client connections, and handles client registration.
     */
    public void startServer() {
        System.out.println("서버가 시작되었습니다. 포트: " + PORT); // "Server started. Port: [PORT]"

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (isRunning) {
                // Accept a new client connection
                Socket clientSocket = serverSocket.accept();

                synchronized (this) {
                    // If the maximum number of players has been reached, reject the connection
                    if (clients.size() >= MAX_PLAYERS) {
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println("현재 게임이 진행 중입니다. 접속할 수 없습니다."); // "The game is currently in progress. Cannot connect."
                        clientSocket.close(); // Close the connection
                        continue; // Skip to the next iteration to accept other clients
                    }

                    // Create a new client handler for the connected client
                    ClientHandler_test clientHandler = new ClientHandler_test(clientSocket, this);
                    clients.add(clientHandler); // Add the client to the client list
                    new Thread(clientHandler).start(); // Start the client handler thread
                    updateClientStates(); // Update client states based on the new connection
                }
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage()); // "Server error: [Error Message]"
        } finally {
            shutdownServer(); // Ensure the server is properly shut down
        }
    }

    /**
     * Shuts down the server gracefully.
     * Notifies all clients about the shutdown and closes all client connections.
     */
    private synchronized void shutdownServer() {
        broadcast("서버가 종료됩니다."); // "The server is shutting down."
        scheduler.shutdown(); // Shutdown the scheduler to stop any ongoing tasks
        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.closeConnection(); // Close each client's connection
            }
        }
        System.out.println("서버가 종료되었습니다."); // "The server has been shut down."
    }

    /**
     * Ends the current game, announces final results, and shuts down the server.
     * Sorts players based on their scores and broadcasts the final rankings.
     */
    public synchronized void endGame() {
        remainingTurns = 0; // Set remaining turns to zero
        sendGameStatus();    // Broadcast the final game status
        broadcast("게임이 종료되었습니다!"); // "The game has ended!"

        // Create a sorted list of clients based on their scores in descending order
        List<ClientHandler_test> sortedClients;
        synchronized (clients) {
            sortedClients = new ArrayList<>(clients);
        }
        sortedClients.sort(Comparator.comparingInt(ClientHandler_test::getScore).reversed());

        // Build the final results message
        StringBuilder finalResults = new StringBuilder("최종 결과:\n"); // "Final Results:"
        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            finalResults.append(client.getNickname())
                        .append(" : ")
                        .append(client.getScore())
                        .append("점 / ")
                        .append(rank + 1)
                        .append("위\n"); // "[Nickname] : [Score] points / [Rank] place"
        }

        // Send personalized final results to each client
        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            StringBuilder personalMessage = new StringBuilder(finalResults.toString());
            personalMessage.append("당신의 최종 점수: ")
                           .append(client.getScore())
                           .append("점, 순위: ")
                           .append(rank + 1)
                           .append("위\n"); // "Your final score: [Score] points, Rank: [Rank] place"

            // Congratulate the winner
            if (rank == 0) {
                personalMessage.append("축하드립니다! 우승입니다!\n"); // "Congratulations! You are the winner!"
            }

            client.sendMessage(personalMessage.toString()); // Send the final results to the client
        }

        broadcast("SERVER_SHUTDOWN"); // Notify all clients that the server is shutting down

        // Cancel any ongoing time update tasks
        if (timeUpdateTask != null && !timeUpdateTask.isCancelled()) {
            timeUpdateTask.cancel(true);
        }

        scheduler.shutdown();    // Shutdown the scheduler
        gameStarted = false;    // Reset the game started flag
        isRunning = false;      // Stop the server loop

        synchronized (clients) {
            for (ClientHandler_test client : clients) {
                client.closeConnection(); // Close each client's connection
            }
            clients.clear(); // Clear the client list
        }

        System.out.println("게임이 종료되었습니다. 서버를 종료합니다."); // "The game has ended. Shutting down the server."
    }
}
