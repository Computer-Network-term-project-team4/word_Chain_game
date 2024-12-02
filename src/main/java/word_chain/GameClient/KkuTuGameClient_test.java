package word_chain.GameClient;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * KkuTuGameClient_test is a Java Swing-based client for a word-chain game.
 * It connects to a server, handles user nickname registration, and manages game interactions.
 */
public class KkuTuGameClient_test {
    // Main window frame
    private JFrame frame;
    // Input field for user messages
    private JTextField inputField;
    // Text area to display chat and game messages
    private JTextArea chatArea;
    // Labels to display user information and game status
    private JLabel nicknameLabel;
    private JLabel timerLabel;
    private JLabel turnsLabel;
    private JLabel scoreLabel;
    private JLabel participantsLabel;
    // Default background color of chatArea to revert when not user's turn
    private Color defaultChatAreaBackground;
    // Streams for communication with the server
    private PrintWriter out;
    private BufferedReader in;
    // Socket for server connection
    private Socket socket;
    private String scoreStr; // add field to save score


    // User's nickname
    private String nickname;
    // Counter to track nickname input attempts
    private int attempt = 0; // Number of nickname attempts

    /**
     * Entry point of the client application.
     * Initializes the client on the Event Dispatch Thread (EDT).
     *
     * @param args Command-line arguments (not used).
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(KkuTuGameClient_test::new);
    }

    /**
     * Constructor initializes the GUI and attempts to connect to the server.
     */
    public KkuTuGameClient_test() {
        setupGUI();       // Initialize the graphical user interface
        connectToServer(); // Attempt to connect to the game server
    }

    /**
     * Sets up the graphical user interface using Java Swing components.
     */
    private void setupGUI() {
        // Initialize the main application window
        frame = new JFrame("끝말잇기 게임 클라이언트"); // "Word Chain Game Client"
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Dispose frame on close
        frame.setSize(400, 600); // Set initial window size

        // Add a window listener to handle resource cleanup when the window is closing
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeResources(); // Ensure resources are closed properly
            }
        });

        // Create a main panel with GroupLayout for flexible layout management
        JPanel mainPanel = new JPanel();
        GroupLayout layout = new GroupLayout(mainPanel);
        mainPanel.setLayout(layout);
        layout.setAutoCreateGaps(true); // Automatically add gaps between components
        layout.setAutoCreateContainerGaps(true); // Automatically add gaps between components and container

        // Initialize UI components
        nicknameLabel = new JLabel("닉네임: "); // "Nickname: "
        timerLabel = new JLabel("타이머: -"); // "Timer: -"
        turnsLabel = new JLabel("남은 턴 수: -"); // "Remaining Turns: -"
        participantsLabel = new JLabel("참여자: -"); // "Participants: -"
        chatArea = new JTextArea(); // Area to display chat and game messages
        chatArea.setEditable(false); // Make chatArea non-editable
        JScrollPane chatScrollPane = new JScrollPane(chatArea); // Add scroll capability to chatArea
        defaultChatAreaBackground = chatArea.getBackground(); // Store the default background color

        inputField = new JTextField(); // Field for user to input messages
        inputField.setEnabled(false); // Initially disable input field until it's the user's turn
        inputField.addActionListener(e -> sendMessage()); // Add action listener to handle message sending

        scoreLabel = new JLabel("누적 점수: 0"); // "Total Score: 0"

        // Define the horizontal layout grouping
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(nicknameLabel)
                    .addComponent(timerLabel)
                    .addComponent(turnsLabel))
                .addComponent(participantsLabel)
                .addComponent(chatScrollPane)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(inputField)
                    .addComponent(scoreLabel))
        );

        // Define the vertical layout grouping
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(nicknameLabel)
                    .addComponent(timerLabel)
                    .addComponent(turnsLabel))
                .addComponent(participantsLabel)
                .addComponent(chatScrollPane)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(inputField)
                    .addComponent(scoreLabel))
        );

        // Add the main panel to the frame and make it visible
        frame.add(mainPanel);
        frame.setVisible(true);
    }

    /**
     * Attempts to connect to the game server.
     * Sets up communication streams and starts listening for server messages.
     */
    private void connectToServer() {
        try {
            // Establish a socket connection to the server running on localhost at port 12345
            socket = new Socket("localhost", 12345);
            chatArea.append("서버에 연결되었습니다.\n"); // "Connected to the server."

            // Initialize output and input streams for communication
            out = new PrintWriter(socket.getOutputStream(), true); // Auto-flush enabled
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Start a new thread to listen for messages from the server
            new Thread(this::listenToServer).start();

            // Read the initial message from the server
            String serverMessage = in.readLine();
            if (serverMessage != null) {
                if (serverMessage.equals("닉네임을 입력하세요:")) { // "Please enter your nickname:"
                    promptForNickname(); // Prompt the user to enter a nickname
                } else {
                    handleServerMessage(serverMessage); // Handle other initial messages
                }
            }
        } catch (IOException e) {
            // Append a failure message to chatArea if the connection fails
            chatArea.append("서버 연결 실패.\n"); // "Failed to connect to the server."
        }
    }

    /**
     * Prompts the user to enter a nickname using a dialog.
     * Sends the nickname to the server if valid, otherwise displays an error and closes the client.
     */
    private void promptForNickname() {
        SwingUtilities.invokeLater(() -> {
            // Display an input dialog to prompt the user for a nickname
            nickname = JOptionPane.showInputDialog(frame, "닉네임을 입력하세요:"); // "Please enter your nickname:"

            if (nickname != null && !nickname.trim().isEmpty()) {
                // If a valid nickname is entered, send it to the server
                out.println(nickname.trim());
                // Further UI updates (like setting nicknameLabel) will be handled upon server confirmation
            } else {
                // If the nickname is invalid or input is canceled, show an error message
                JOptionPane.showMessageDialog(frame, "유효하지 않은 닉네임입니다. 종료합니다.", "닉네임 오류", JOptionPane.ERROR_MESSAGE); // "Invalid nickname. Exiting.", "Nickname Error"
                inputField.setEnabled(false); // Disable the input field
                chatArea.setBackground(defaultChatAreaBackground); // Reset chatArea background color
                closeResources(); // Close client resources
            }

            attempt++; // Increment the attempt counter
        });
    }

    /**
     * Continuously listens for messages from the server in a separate thread.
     * Processes each received message accordingly.
     */
    private void listenToServer() {
        try {
            String message;
            // Continuously read messages from the server
            while ((message = in.readLine()) != null) {
                System.out.println("서버에서 받은 메시지: " + message); // "Message received from server: "

                if (message.startsWith("STATUS_UPDATE")) {
                    updateStatus(message); // Handle status updates
                } else if (message.startsWith("PARTICIPANTS_UPDATE")) {
                    updateParticipants(message); // Handle participants updates
                } else if (message.contains("누적 점수: ")) { // "Total Score: "
                    updateScore(message); // Handle score updates
                } else {
                    handleServerMessage(message); // Handle other server messages
                }
            }
        } catch (IOException e) {
            // Append a message to chatArea if the connection is lost
            chatArea.append("서버와의 연결이 끊겼습니다.\n"); // "Disconnected from the server."
        }
    }

    /**
     * Handles various types of messages received from the server.
     * Updates the UI based on the message content.
     *
     * @param message The message received from the server.
     */
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.equals("현재 게임이 진행 중입니다. 접속할 수 없습니다.")) { // "The game is currently in progress. Cannot connect."
                // Show an error dialog indicating that connection is not possible
                JOptionPane.showMessageDialog(frame, message, "연결 불가", JOptionPane.ERROR_MESSAGE); // "Cannot Connect"
                closeResources(); // Close client resources
            } else if (message.equals("대기 중입니다.")) { // "Waiting."
                inputField.setEnabled(false); // Disable the input field
                chatArea.append("대기 중입니다. 다른 플레이어를 기다리는 중...\n"); // "Waiting. Waiting for other players..."
            } else if (message.equals("닉네임을 입력하세요:")) { // "Please enter your nickname:"
                if (attempt == 0) {
                    promptForNickname(); // Prompt for nickname on first attempt
                }
                // Additional attempts can be handled here if needed
            } else if (message.equals("유효하지 않은 닉네임입니다. 연결을 종료합니다.")) { // "Invalid nickname. Closing connection."
                // Show an error dialog indicating invalid nickname and close the client
                JOptionPane.showMessageDialog(frame, message, "닉네임 오류", JOptionPane.ERROR_MESSAGE); // "Nickname Error"
                closeResources(); // Close client resources
            } else if (message.equals("이미 사용 중인 닉네임입니다. 닉네임을 다시 입력하세요.")) { // "Nickname already in use. Please enter a different nickname."
                // Show a warning dialog indicating nickname duplication
                JOptionPane.showMessageDialog(frame, message, "닉네임 중복", JOptionPane.WARNING_MESSAGE); // "Nickname Duplicate"
                promptForNickname(); // Prompt the user to enter a different nickname
            } else if (message.equals("닉네임이 성공적으로 설정되었습니다.")) { // "Nickname has been successfully set."
                // Update the UI to reflect the successfully set nickname
                nicknameLabel.setText("닉네임: " + nickname.trim()); // "Nickname: [nickname]"
                frame.setTitle(nickname + "의 끝말잇기 게임 클라이언트"); // "[nickname]'s Word Chain Game Client"
                chatArea.append("닉네임이 설정되었습니다. 게임을 기다리고 있습니다...\n"); // "Nickname has been set. Waiting for the game..."
                inputField.setEnabled(false); // Disable the input field until it's the user's turn
            } else {
                // For all other messages, append them to the chat area and handle game-specific messages
                System.out.println(message); // Print the message to the console for debugging
                chatArea.append(message + "\n"); // Append the message to the chat area
                chatArea.setCaretPosition(chatArea.getDocument().getLength()); // Auto-scroll to the latest message
                handleGameMessages(message); // Handle game-specific messages
            }
        });
    }

    /**
     * Handles game-specific messages to update the UI based on the game state.
     * Changes the chatArea background color to indicate the user's turn.
     *
     * @param message The game-related message from the server.
     */
    private void handleGameMessages(String message) {
        if (message.contains("당신의 차례입니다!")) { // "It's your turn!"
            inputField.setEnabled(true); // Enable the input field for user input
            inputField.requestFocusInWindow(); // Request focus on the input field for immediate typing

            // Change the chatArea background color to a light yellow to indicate it's the user's turn
            chatArea.setBackground(new Color(255, 255, 200)); // Light Yellow
        } else if (message.contains("현재 당신의 차례가 아닙니다.") || // "It's not your turn right now."
                   message.contains("시간이 초과되었습니다") || // "Time has expired."
                   message.contains("당신은 게임에서 나갔습니다.")) { // "You have left the game."
            inputField.setEnabled(false); // Disable the input field as it's not the user's turn

            // Revert the chatArea background color to its default when it's not the user's turn
            chatArea.setBackground(defaultChatAreaBackground);

            if (message.contains("당신은 게임에서 나갔습니다.")) { // "You have left the game."
                closeResources(); // Close client resources if the user has left the game
                chatArea.setBackground(defaultChatAreaBackground); // Ensure background color is reset
            }
        } else if (message.equals("SERVER_SHUTDOWN")) { // "SERVER_SHUTDOWN" signal from the server
            inputField.setEnabled(false); // Disable the input field
            chatArea.setBackground(defaultChatAreaBackground); // Reset chatArea background color
            closeResources(); // Close client resources as the server is shutting down
        }
    }

    /**
     * Updates the status labels (timer and remaining turns) based on the server message.
     *
     * @param statusMessage The status update message from the server.
     */
    private void updateStatus(String statusMessage) {
        // Split the status message by '|' to handle multiple status updates in one message
        String[] parts = statusMessage.split("\\|");
        for (String part : parts) {
            if (part.startsWith("TIME:")) { // "TIME:[value]"
                timerLabel.setText("타이머: " + part.substring(5)); // Update the timer label
            } else if (part.startsWith("TURNS:")) { // "TURNS:[value]"
                turnsLabel.setText("남은 턴 수: " + part.substring(6)); // Update the remaining turns label
            }
        }
    }

    /**
     * Updates the participants label based on the server message.
     *
     * @param participantsMessage The participants update message from the server.
     */
    private void updateParticipants(String participantsMessage) {
        // Split the participants message by '|' to extract the list of participants
        String[] parts = participantsMessage.split("\\|");
        if (parts.length > 1) {
            participantsLabel.setText("참여자: " + parts[1].trim()); // "Participants: [list]"
        }
    }

    /**
     * Updates the score label based on the server message.
     *
     * @param message The score update message from the server.
     */
    private void updateScore(String message) {
        // Find the index where "누적 점수: " (Total Score: ) starts
        int index = message.indexOf("누적 점수: ");
        if (index != -1) {
            // Extract the score value from the message
            scoreStr = message.substring(index + 7).trim().split("\\s+")[0];
            // Update the score label with the new score
            scoreLabel.setText("누적 점수: " + scoreStr); // "Total Score: [score]"
        }
        // Append a confirmation message to the chat area indicating a valid word and the score earned
        chatArea.append("\n유효한 단어입니다. 점수: " + scoreStr + "\n"); // "Valid word. Score: [score]"
    }

    /**
     * Sends the user's message to the server when the Enter key is pressed in the input field.
     * Also resets the chatArea background color and disables the input field until it's the user's turn again.
     */
    private void sendMessage() {
        // Append a message indicating that the word is being judged
        chatArea.append("판별 중 입니다....\n"); // "Judging..."

        // Retrieve and trim the user's input from the input field
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            // Send the user's message to the server in lowercase
            out.println(message.toLowerCase());
            // Clear the input field after sending the message
            inputField.setText("");
            // Reset the chatArea background color to default as the user's turn is over
            chatArea.setBackground(defaultChatAreaBackground);
            // Disable the input field until it's the user's turn again
            inputField.setEnabled(false);

            // Example: Use scoreStr after sending a message
            if (scoreStr != null) {
                chatArea.append("현재 점수는 " + scoreStr + "입니다.\n"); // "Your current score is [score]."
            }
        }
    }

    /**
     * Closes all resources associated with the client, including streams and the socket.
     * Disposes of the main application window.
     */
    private void closeResources() {
        try {
            if (out != null) out.close(); // Close the output stream
        } catch (Exception ignored) {}
        try {
            if (in != null) in.close(); // Close the input stream
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close(); // Close the socket if it's open
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> frame.dispose()); // Dispose of the main frame on the EDT
    }
}
