package word_chain.GameClient;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class KkuTuGameClient_test {
    private JFrame frame;
    private JTextField inputField;
    private JTextArea chatArea;
    private JLabel nicknameLabel;
    private JLabel timerLabel;
    private JLabel turnsLabel;
    private JLabel scoreLabel;
    private JLabel participantsLabel;

    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    private String nickname;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KkuTuGameClient_test::new);
    }

    public KkuTuGameClient_test() {
        setupGUI();
        connectToServer();
    }

    // GUI 초기화
    private void setupGUI() {
        frame = new JFrame("끝말잇기 게임 클라이언트");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(400, 600);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeResources();
            }
        });

        // GroupLayout을 사용하여 UI 구성
        JPanel mainPanel = new JPanel();
        GroupLayout layout = new GroupLayout(mainPanel);
        mainPanel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        // UI 요소 초기화
        nicknameLabel = new JLabel("닉네임: ");
        timerLabel = new JLabel("타이머: -");
        turnsLabel = new JLabel("남은 턴 수: -");
        participantsLabel = new JLabel("참여자: -");
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        inputField = new JTextField();
        inputField.setEnabled(false);
        inputField.addActionListener(e -> sendMessage());

        scoreLabel = new JLabel("누적 점수: 0");

        // GroupLayout으로 레이아웃 설정
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

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // 서버 연결
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            chatArea.append("서버에 연결되었습니다.\n");

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            new Thread(this::listenToServer).start();

            String serverMessage = in.readLine();
            if (serverMessage != null) {
                if (serverMessage.equals("닉네임을 입력하세요:")) {
                    promptForNickname();
                } else {
                    handleServerMessage(serverMessage);
                }
            }
        } catch (IOException e) {
            chatArea.append("서버 연결 실패.\n");
        }
    }

    // 닉네임 입력
    private void promptForNickname() {
        nickname = JOptionPane.showInputDialog(frame, "닉네임을 입력하세요:");
        if (nickname != null && !nickname.trim().isEmpty()) {
            out.println(nickname.trim());
            nicknameLabel.setText("닉네임: " + nickname.trim());
            frame.setTitle(nickname + "의 끝말잇기 게임 클라이언트");
        } else {
            JOptionPane.showMessageDialog(frame, "유효하지 않은 닉네임입니다. 종료합니다.", "닉네임 오류", JOptionPane.ERROR_MESSAGE);
            inputField.setEnabled(false);
            closeResources();
        }
    }

    // 서버 메시지 수신
    private void listenToServer() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("서버에서 받은 메시지: " + message);
                if (message.startsWith("STATUS_UPDATE")) {
                    updateStatus(message);
                } else if (message.startsWith("PARTICIPANTS_UPDATE")) {
                    updateParticipants(message);
                } else if (message.contains("누적 점수: ")) {
                    
                    updateScore(message);
                    

                } else {
                    handleServerMessage(message);
                   
                }
            }
        } catch (IOException e) {
            chatArea.append("서버와의 연결이 끊겼습니다.\n");
        }
    }

    // 서버 메시지 처리
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.equals("현재 게임이 진행 중입니다. 접속할 수 없습니다.")) {
                JOptionPane.showMessageDialog(frame, message, "연결 불가", JOptionPane.ERROR_MESSAGE);
                closeResources();
            } else if (message.equals("대기 중입니다.")) {
                inputField.setEnabled(false);
                chatArea.append("대기 중입니다. 다른 플레이어를 기다리는 중...\n");
            } else {
                System.out.println(message);
                chatArea.append(message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength()); // 자동 스크롤
                handleGameMessages(message);
            }
        });
    }

    // 게임 메시지 처리
    private void handleGameMessages(String message) {
        if (message.contains("당신의 차례입니다!")) {
            inputField.setEnabled(true);
            inputField.requestFocusInWindow(); // 입력 필드에 포커스
        } else if (message.contains("현재 당신의 차례가 아닙니다.") ||
                   message.contains("시간이 초과되었습니다") ||
                   message.contains("당신은 게임에서 나갔습니다.")) {
            inputField.setEnabled(false);
            if (message.contains("당신은 게임에서 나갔습니다.")) {
                closeResources();
            }
        } else if (message.equals("SERVER_SHUTDOWN")) {
            inputField.setEnabled(false);
            closeResources();
        }
    }

    // 상태 업데이트
    private void updateStatus(String statusMessage) {
        String[] parts = statusMessage.split("\\|");
        for (String part : parts) {
            if (part.startsWith("TIME:")) {
                timerLabel.setText("타이머: " + part.substring(5));
            } else if (part.startsWith("TURNS:")) {
                turnsLabel.setText("남은 턴 수: " + part.substring(6));
            }
        }
    }

    // 참여자 업데이트
    private void updateParticipants(String participantsMessage) {
        String[] parts = participantsMessage.split("\\|");
        if (parts.length > 1) {
            participantsLabel.setText("참여자: " + parts[1].trim());
        }
    }

    // 점수 업데이트
    private void updateScore(String message) {
        int index = message.indexOf("누적 점수: ");
        String scoreStr = message.substring(index + 7).trim().split("\\s+")[0];
        if (index != -1) {
            
            scoreLabel.setText("누적 점수: " + scoreStr);
        }
        chatArea.append("\n 유효한 단어입니다. 점수: "+ scoreStr);
    }

    // 메시지 전송
    private void sendMessage() {
        chatArea.append("판별 중 입니다....\n");
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message.toLowerCase());
            inputField.setText("");
            inputField.setEnabled(false);
        }
    }

    // 리소스 정리
    private void closeResources() {
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> frame.dispose());
    }
}
