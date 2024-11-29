package word_chain.GameClient;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class KkuTuGameClient_test {
    private JFrame frame; // GUI 프레임
    private JTextField inputField; // 입력 필드
    private JTextArea chatArea; // 채팅 영역
    private JLabel nicknameLabel; // 닉네임 표시 라벨
    private JLabel timerLabel; // 타이머 표시 라벨
    private JLabel turnsLabel; // 남은 턴 수 표시 라벨
    private JLabel scoreLabel; // 누적 점수 표시 라벨

    private PrintWriter out; // 서버로 메시지를 보내는 출력 스트림
    private BufferedReader in; // 서버로부터 메시지를 받는 입력 스트림
    private Socket socket; // 클라이언트 소켓

    private String nickname; // 클라이언트의 닉네임

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KkuTuGameClient_test::new);
    }

    // 생성자: GUI 설정 및 서버 연결
    public KkuTuGameClient_test() {
        setupGUI();
        connectToServer();
    }

    // GUI 설정
    private void setupGUI() {
        frame = new JFrame("끝말잇기 게임 클라이언트");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(400, 600);

        // 창 닫기 이벤트 처리
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeResources();
            }
        });

        // 상단 패널: 닉네임, 타이머, 남은 턴 수 표시
        JPanel topPanel = new JPanel(new GridLayout(1, 3));
        nicknameLabel = new JLabel("닉네임: ");
        timerLabel = new JLabel("타이머: -");
        turnsLabel = new JLabel("남은 턴 수: -");
        topPanel.add(nicknameLabel);
        topPanel.add(timerLabel);
        topPanel.add(turnsLabel);
        frame.add(topPanel, BorderLayout.NORTH);

        // 채팅 영역
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // 입력 필드와 누적 점수 표시 패널
        JPanel bottomPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());
        inputField.setEnabled(false); // 처음에는 입력 비활성화
        bottomPanel.add(inputField, BorderLayout.CENTER);

        scoreLabel = new JLabel("누적 점수: 0");
        bottomPanel.add(scoreLabel, BorderLayout.SOUTH);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // 서버 연결
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            chatArea.append("서버에 연결되었습니다.\n");

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            new Thread(this::listenToServer).start(); // 서버 메시지 수신 스레드 시작

            // 서버에서 닉네임을 요청하는 메시지를 받을 때까지 대기
            String serverMessage = in.readLine();
            if (serverMessage != null && serverMessage.equals("닉네임을 입력하세요:")) {
                nickname = JOptionPane.showInputDialog(frame, "닉네임을 입력하세요:");
                if (nickname != null && !nickname.trim().isEmpty()) {
                    out.println(nickname.trim());
                    nicknameLabel.setText("닉네임: " + nickname.trim());
                } else {
                    chatArea.append("유효하지 않은 닉네임입니다. 종료합니다.\n");
                    inputField.setEnabled(false);
                    closeResources();
                    return;
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
                if (message.startsWith("STATUS_UPDATE")) {
                    updateStatus(message);
                    continue;
                }

                // 누적 점수 업데이트
                if (message.contains("누적 점수: ")) {
                    int index = message.indexOf("누적 점수: ");
                    String scoreStr = message.substring(index + 7).trim();
                    String[] parts = scoreStr.split("\\s+");
                    int cumulativeScore = Integer.parseInt(parts[0]);
                    SwingUtilities.invokeLater(() -> scoreLabel.setText("누적 점수: " + cumulativeScore));
                }

                // 일반 메시지는 채팅창에 표시
                chatArea.append(message + "\n");

                // 서버 메시지에 따라 동작
                if (message.contains("현재 게임이 진행 중입니다. 접속할 수 없습니다.")) {
                    JOptionPane.showMessageDialog(frame, "게임이 진행 중이어서 접속할 수 없습니다.", "알림", JOptionPane.INFORMATION_MESSAGE);
                    closeResources();
                    break;
                } else if (message.contains("당신의 차례입니다!")) {
                    inputField.setEnabled(true);
                    inputField.requestFocus();
                } else if (message.contains("현재 당신의 차례가 아닙니다.") || message.contains("대기 중입니다.")) {
                    inputField.setEnabled(false);
                } else if (message.contains("시간이 초과되었습니다")) {
                    inputField.setEnabled(false);
                } else if (message.contains("당신은 게임에서 나갔습니다.")) {
                    inputField.setEnabled(false);
                    closeResources();
                    break;
                } else if (message.equals("SERVER_SHUTDOWN")) {
                    inputField.setEnabled(false);
                    closeResources();
                    break;
                }
            }
        } catch (IOException e) {
            chatArea.append("서버와의 연결이 끊겼습니다.\n");
        }
    }

    // 상태 업데이트 처리
    private void updateStatus(String statusMessage) {
        // STATUS_UPDATE|TIME:x|TURNS:y 형식의 메시지 파싱
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

        // GUI 업데이트는 Event Dispatch Thread에서 실행되어야 함
        SwingUtilities.invokeLater(() -> {
            if (finalTimeLeft >= 0) {
                timerLabel.setText("타이머: " + finalTimeLeft + "초");
            }
            if (finalTurnsLeft >= 0) {
                turnsLabel.setText("남은 턴 수: " + finalTurnsLeft);
            }
        });
    }

    // 메시지 전송
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message); // 서버로 메시지 전송
            inputField.setText(""); // 입력 필드 초기화
            inputField.setEnabled(false); // 메시지 전송 후 입력 비활성화
            timerLabel.setText("타이머: -"); // 타이머 리셋
        }
    }

    // 자원 해제
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
    }
}
