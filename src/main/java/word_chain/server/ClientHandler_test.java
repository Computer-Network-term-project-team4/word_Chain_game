package word_chain.server;

import java.io.*;
import java.net.*;

/**
 * ClientHandler_test 클래스
 * - 각 클라이언트와의 개별 연결을 처리하는 역할을 담당
 * - 클라이언트의 닉네임, 점수 관리 및 서버와의 메시지 송수신을 처리
 */
public class ClientHandler_test implements Runnable {
    private final Socket socket; // 클라이언트 소켓
    private final Server_test server; // 서버 참조
    private String nickname; // 클라이언트의 닉네임
    private PrintWriter out; // 클라이언트로 메시지를 보내는 출력 스트림
    private int score = 0; // 클라이언트의 점수

    /**
     * 생성자
     * @param socket 클라이언트와의 연결을 담당하는 소켓
     * @param server 서버 참조 객체
     */
    public ClientHandler_test(Socket socket, Server_test server) {
        this.socket = socket; // 클라이언트 소켓 초기화
        this.server = server; // 서버 객체 초기화
    }

    /**
     * 클라이언트의 닉네임 반환
     * @return 닉네임
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 클라이언트의 점수 반환
     * @return 점수
     */
    public int getScore() {
        return score;
    }

    /**
     * 클라이언트의 점수를 추가
     * @param points 추가할 점수
     */
    public void addScore(int points) {
        score += points; // 현재 점수에 추가
    }

    /**
     * 클라이언트로 메시지를 전송
     * @param message 전송할 메시지
     */
    public void sendMessage(String message) {
        if (out != null) { // 출력 스트림이 초기화된 경우
            out.println(message); // 클라이언트로 메시지 전송
        }
    }

    /**
     * 클라이언트와의 통신을 처리하는 메서드 (스레드 실행 메서드)
     */
    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 클라이언트 입력 스트림 생성
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true) // 클라이언트 출력 스트림 생성
        ) {
            this.out = writer; // 출력 스트림을 클래스 변수로 초기화

            // 닉네임 입력 요청
            writer.println("닉네임을 입력하세요:");
            nickname = in.readLine(); // 클라이언트가 전송한 닉네임 읽기
            if (nickname == null || nickname.trim().isEmpty()) { // 닉네임이 없거나 유효하지 않은 경우
                writer.println("유효하지 않은 닉네임입니다. 연결을 종료합니다.");
                socket.close(); // 소켓 닫기
                return; // 메서드 종료
            }

            nickname = nickname.trim();

            // 닉네임이 유효한 경우
            System.out.println("클라이언트 연결: 닉네임 = " + nickname); // 서버 로그에 닉네임 출력
            server.broadcast(nickname + " 님이 접속했습니다."); // 다른 클라이언트에게 접속 알림
            server.updateClientStates(); // 서버 상태 업데이트 (게임 시작 여부 등)

            // 클라이언트 메시지 처리
            String message;
            while ((message = in.readLine()) != null) { // 클라이언트로부터 메시지 읽기
                if (message.equalsIgnoreCase("exit")) { // 'exit' 명령어 처리
                    break; // 반복문 종료
                }
                if (message.equalsIgnoreCase("end game")) { // 'end game' 명령어 처리
                    server.endGame();
                    break;
                }
                server.processWord(message, this); // 서버에서 단어 처리
            }
        } catch (IOException e) {
            // 클라이언트 처리 중 에러 발생 시 로그 출력
            System.err.println("클라이언트 처리 오류: " + e.getMessage());
        } finally {
            // 클라이언트 종료 처리
            server.removeClient(this); // 서버에서 클라이언트 제거
            closeConnection(); // 소켓 연결 종료
        }
    }

    /**
     * 클라이언트와의 연결 닫기
     */
    public void closeConnection() {
        try {
            socket.close(); // 소켓 닫기
        } catch (IOException e) {
            // 소켓 닫는 중 오류 발생 시 로그 출력
            System.err.println("클라이언트 연결 종료 오류: " + e.getMessage());
        }
    }
}
