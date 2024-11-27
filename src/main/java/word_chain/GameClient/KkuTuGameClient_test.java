package word_chain.GameClient;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class KkuTuGameClient_test {
    private JFrame frame;
    private JTextField inputField;
    private JTextArea chatArea;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KkuTuGameClient_test::new);
    }

    public KkuTuGameClient_test() {
        setupGUI();
        connectToServer();
    }

    private void setupGUI() {
        frame = new JFrame("끝말잇기 게임 클라이언트");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 600);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());
        inputField.setEnabled(false); // 처음에는 입력 비활성화
        frame.add(inputField, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 12345);
            chatArea.append("서버에 연결되었습니다.\n");

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            new Thread(() -> listenToServer()).start();

            // 서버에서 닉네임을 요청하는 메시지를 받을 때까지 대기
            String serverMessage = in.readLine();
            if (serverMessage != null && serverMessage.equals("닉네임을 입력하세요:")) {
                nickname = JOptionPane.showInputDialog(frame, "닉네임을 입력하세요:");
                if (nickname != null && !nickname.trim().isEmpty()) {
                    out.println(nickname.trim());
                } else {
                    chatArea.append("유효하지 않은 닉네임입니다. 종료합니다.\n");
                    socket.close();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            chatArea.append("서버 연결 실패.\n");
        }
    }

    private void listenToServer() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                chatArea.append(message + "\n");

                if (message.contains("당신의 차례입니다!")) {
                    inputField.setEnabled(true);
                    inputField.requestFocus();
                } else if (message.contains("현재 당신의 차례가 아닙니다.") || message.contains("대기 중입니다.")) {
                    inputField.setEnabled(false);
                } else if (message.contains("게임이 종료되었습니다") || message.contains("서버가 종료됩니다.")) {
                    inputField.setEnabled(false);
                    out.close();
                    in.close();
                    break;
                }
            }
        } catch (IOException e) {
            chatArea.append("서버와의 연결이 끊겼습니다.\n");
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            inputField.setText("");
            inputField.setEnabled(false); // 메시지 전송 후 입력 비활성화
        }
    }
}
