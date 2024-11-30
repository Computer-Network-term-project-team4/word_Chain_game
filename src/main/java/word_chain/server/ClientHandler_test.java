package word_chain.server;

import java.io.*;
import java.net.Socket;

/**
 * ClientHandler_test 클래스
 * - 각 클라이언트와의 개별 연결을 처리
 * - 클라이언트의 닉네임, 점수 관리 및 서버와의 메시지 송수신을 담당
 */
public class ClientHandler_test implements Runnable {
    private final Socket socket;
    private final Server_test server;
    private String nickname;
    private PrintWriter out;
    private int score = 0;

    public ClientHandler_test(Socket socket, Server_test server) {
        this.socket = socket;
        this.server = server;
    }

    public String getNickname() {
        return nickname;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        score += points;
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = writer;

            // 닉네임 입력 요청
            writer.println("닉네임을 입력하세요:");
            nickname = in.readLine();

            if (nickname == null || nickname.trim().isEmpty()) {
                writer.println("유효하지 않은 닉네임입니다. 연결을 종료합니다.");
                return;
            }

            nickname = nickname.trim();
            System.out.println("클라이언트 연결: 닉네임 = " + nickname);
            server.broadcast(nickname + " 님이 접속했습니다.");
            server.broadcastParticipants();
            server.updateClientStates();

            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("exit")) {
                    break;
                }
                if (message.equalsIgnoreCase("end game")) {
                    server.endGame();
                    break;
                }
                server.processWord(message, this);
            }
        } catch (IOException e) {
            System.err.println("클라이언트 처리 오류: " + e.getMessage());
        } finally {
            server.removeClient(this);
            closeConnection();
        }
    }

    public void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("클라이언트 연결 종료 오류: " + e.getMessage());
        }
    }
}
