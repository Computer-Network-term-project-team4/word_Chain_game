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

        // 상단 패널 설정
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JPanel firstRow = new JPanel(new GridLayout(1, 3));
        nicknameLabel = new JLabel("닉네임: ");
        timerLabel = new JLabel("타이머: -");
        turnsLabel = new JLabel("남은 턴 수: -");
        firstRow.add(nicknameLabel);
        firstRow.add(timerLabel);
        firstRow.add(turnsLabel);

        participantsLabel = new JLabel("참여자: -");
        participantsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        topPanel.add(firstRow);
        topPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        topPanel.add(participantsLabel);

        frame.add(topPanel, BorderLayout.NORTH);

        // 채팅 영역 설정
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        frame.add(chatScrollPane, BorderLayout.CENTER);

        // 하단 패널 설정
        JPanel bottomPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());
        inputField.setEnabled(false);
        bottomPanel.add(inputField, BorderLayout.CENTER);

        scoreLabel = new JLabel("누적 점수: 0");
        bottomPanel.add(scoreLabel, BorderLayout.SOUTH);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

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

    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.equals("현재 게임이 진행 중입니다. 접속할 수 없습니다.")) {
                JOptionPane.showMessageDialog(frame, message, "연결 불가", JOptionPane.ERROR_MESSAGE);
                closeResources();
            } else {
                chatArea.append(message + "\n");
                handleGameMessages(message);
            }
        });
    }

    private void handleGameMessages(String message) {
        if (message.contains("당신의 차례입니다!")) {
            inputField.setEnabled(true);
            inputField.requestFocus();
        } else if (message.contains("현재 당신의 차례가 아닙니다.") ||
                   message.contains("대기 중입니다.") ||
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

    private void updateStatus(String statusMessage) {
        String[] parts = statusMessage.split("\\|");
        int timeLeft = -1;
        int turnsLeft = -1;

        for (String part : parts) {
            if (part.startsWith("TIME:")) {
                timeLeft = Integer.parseInt(part.substring(5));
            } else if (part.startsWith("TURNS:")) {
                turnsLeft = Integer.parseInt(part.substring(6));
            }
        }

        final int finalTimeLeft = timeLeft;
        final int finalTurnsLeft = turnsLeft;

        SwingUtilities.invokeLater(() -> {
            if (finalTimeLeft >= 0) {
                timerLabel.setText("타이머: " + finalTimeLeft + "초");
            }
            if (finalTurnsLeft >= 0) {
                turnsLabel.setText("남은 턴 수: " + finalTurnsLeft);
            }
        });
    }

    private void updateParticipants(String participantsMessage) {
        String[] parts = participantsMessage.split("\\|");
        if (parts.length > 1) {
            String participants = parts[1].trim();
            SwingUtilities.invokeLater(() -> participantsLabel.setText("참여자: " + participants));
        }
    }

    private void updateScore(String message) {
        int index = message.indexOf("누적 점수: ");
        if (index != -1) {
            String scoreStr = message.substring(index + 7).trim().split("\\s+")[0];
            try {
                int cumulativeScore = Integer.parseInt(scoreStr);
                SwingUtilities.invokeLater(() -> scoreLabel.setText("누적 점수: " + cumulativeScore));
            } catch (NumberFormatException e) {
                System.err.println("점수 파싱 오류: " + e.getMessage());
            }
        }
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            inputField.setText("");
            inputField.setEnabled(false);
            timerLabel.setText("타이머: -");
        }
    }

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
