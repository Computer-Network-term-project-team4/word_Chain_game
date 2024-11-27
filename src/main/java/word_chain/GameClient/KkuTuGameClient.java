package word_chain.GameClient;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KkuTuGameClient {
    private JFrame frame;                  // GUI 메인 창
    private JTextField nicknameField;     // 닉네임 입력 필드
    private JButton connectButton;        // 서버 연결 버튼
    private JTextArea chatArea;           // 메시지 출력 영역
    private JTextField inputField;        // 사용자 입력 필드
    private PrintWriter out;              // 서버 데이터 전송
    private Socket socket;                // 서버 연결 소켓
    private ExecutorService threadPool;   // multithread

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KkuTuGameClient_test::new);
    }

    public KkuTuGameClient() {
        // GUI 초기화
        setupGUI();

        // 멀티쓰레드 환경 설정
        threadPool = Executors.newFixedThreadPool(2); // 메인 쓰레드와 서버 응답 처리 쓰레드
    }

    // Java GUI Preferences 설정
    private void setupGUI() {
        frame = new JFrame("KkuTu Game Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 600);

        // 상단: 닉네임 입력 및 서버 연결
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());
        nicknameField = new JTextField(15);
        connectButton = new JButton("Connect");
        topPanel.add(new JLabel("Nickname:"));
        topPanel.add(nicknameField);
        topPanel.add(connectButton);

        // 중단: 메시지 표시
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        // 하단: 채팅입력
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        inputField = new JTextField();
        JButton sendButton = new JButton("Send");
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        // 전체 설정
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(chatScroll, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // actionlistener
        connectButton.addActionListener(e -> connectToServer());
        sendButton.addActionListener(e -> sendMessage());

        frame.setVisible(true);
    }

    // 서버 연결
    private void connectToServer() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter a nickname!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            socket = new Socket("localhost", 12345); // 서버 IP와 포트
            out = new PrintWriter(socket.getOutputStream(), true);
            chatArea.append("Connected to the server!\n");

            // 서버에 닉네임 전송
            out.println(nickname);

            // 서버 응답 처리
            threadPool.execute(() -> listenToServer(socket));

        } catch (IOException e) {
            chatArea.append("Failed to connect to server.\n");
        }
    }

    // 서버 수신
    private void listenToServer(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                chatArea.append(serverMessage + "\n");
            }
        } catch (IOException e) {
            chatArea.append("Disconnected from server.\n");
        }
    }

    // 메시지 전송
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message); // 서버로 메시지 전송
            inputField.setText(""); // 입력 초기화
        }
    }
}
