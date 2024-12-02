package word_chain.server;

import java.io.*;
import java.net.Socket;

/**
 * ClientHandler_test Class
 * - Handles individual client connections.
 * - Manages the client's nickname, score, and communication with the server.
 * - Processes messages from the client and interacts with the Server_test class.
 */
public class ClientHandler_test implements Runnable {
    // The socket associated with the connected client
    private final Socket socket;
    // Reference to the main server to access shared resources and methods
    private final Server_test server;
    // The client's nickname
    private String nickname;
    // Output stream to send messages to the client
    private PrintWriter out;
    // The client's current score in the game
    private int score = 0;

    /**
     * Constructor initializes the ClientHandler_test with the client's socket and server reference.
     *
     * @param socket The client's socket connection.
     * @param server Reference to the main server instance.
     */
    public ClientHandler_test(Socket socket, Server_test server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * Retrieves the client's nickname.
     *
     * @return The client's nickname.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Retrieves the client's current score.
     *
     * @return The client's score.
     */
    public int getScore() {
        return score;
    }

    /**
     * Adds points to the client's score.
     *
     * @param points The number of points to add.
     */
    public void addScore(int points) {
        score += points;
    }

    /**
     * Sends a message to the client.
     *
     * @param message The message to send.
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * The main execution method for the client handler thread.
     * Manages nickname registration and processes incoming messages from the client.
     */
    @Override
    public void run() {
        try (
            // Initialize input stream to receive messages from the client
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // Initialize output stream to send messages to the client
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = writer; // Assign the output stream for later use

            // Nickname input and duplicate checking loop
            while (true) {
                writer.println("닉네임을 입력하세요:"); // "Please enter your nickname:"
                String inputNickname = in.readLine(); // Read the nickname from the client

                // Check if the client has disconnected or sent an empty nickname
                if (inputNickname == null || inputNickname.trim().isEmpty()) {
                    writer.println("유효하지 않은 닉네임입니다. 연결을 종료합니다."); // "Invalid nickname. Closing connection."
                    return; // Exit the run method, effectively terminating the thread
                }

                inputNickname = inputNickname.trim(); // Remove leading and trailing whitespace

                synchronized (server) { // Synchronize to ensure thread-safe access to server resources
                    if (server.isNicknameDuplicate(inputNickname)) { // Check if the nickname is already in use
                        writer.println("이미 사용 중인 닉네임입니다. 닉네임을 다시 입력하세요."); // "Nickname already in use. Please enter a different nickname."
                    } else {
                        this.nickname = inputNickname; // Assign the nickname to this client handler
                        break; // Exit the loop as a unique nickname has been set
                    }
                }
            }

            // Inform the server console about the new client connection
            System.out.println("클라이언트 연결: 닉네임 = " + nickname); // "Client connected: Nickname = [nickname]"
            // Broadcast to all connected clients that a new player has joined
            server.broadcast(nickname + " 님이 접속했습니다."); // "[nickname] has joined the game."
            // Update the list of participants for all clients
            server.broadcastParticipants();
            // Update client states (e.g., ready status) as necessary
            server.updateClientStates();

            String message;
            // Continuously listen for messages from the client
            while ((message = in.readLine()) != null) {
                // Handle client commands
                if (message.equalsIgnoreCase("exit")) {
                    break; // Exit the loop to disconnect the client
                }
                if (message.equalsIgnoreCase("end game")) {
                    server.endGame(); // Trigger server-side game termination
                    break; // Exit the loop to disconnect the client
                }
                // Process the word sent by the client within the game logic
                server.processWord(message, this);
            }
        } catch (IOException e) {
            // Log any I/O errors that occur during communication
            System.err.println("클라이언트 처리 오류: " + e.getMessage()); // "Client handling error: [error message]"
        } finally {
            // Ensure the client is removed from the server's client list upon disconnection
            server.removeClient(this);
            // Close the client's socket connection
            closeConnection();
        }
    }

    /**
     * Closes the client's socket connection.
     * Logs any errors that occur during the closure.
     */
    public void closeConnection() {
        try {
            socket.close(); // Attempt to close the socket connection
        } catch (IOException e) {
            // Log any errors that occur while closing the socket
            System.err.println("클라이언트 연결 종료 오류: " + e.getMessage()); // "Client connection closure error: [error message]"
        }
    }
}
