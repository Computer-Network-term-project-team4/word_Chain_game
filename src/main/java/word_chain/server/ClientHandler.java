package word_chain.server;

import java.io.*;
import java.net.*;

/**
 * 클라이언트 핸들러
 * - 각 클라이언트를 별도의 스레드로 처리하며, 클라이언트와의 데이터 송수신.
 */
public class ClientHandler implements Runnable {
    private Socket clientSocket; // 현재 클라이언트와의 연결을 담당하는 소켓
    private Server server; // 서버의 참조 (서버와 클라이언트 간 정보 교환을 위해 필요)
    private int clientId; // 클라이언트의 고유 ID (서버에서 부여)
    private boolean isActive = false; // 클라이언트 활성/대기 상태 (활성화된 경우만 게임 참여 가능)

    /**
     * 생성자
     * @param clientSocket 클라이언트와의 연결을 담당하는 소켓
     * @param server 서버 참조
     * @param clientId 클라이언트 고유 ID
     */
    public ClientHandler(Socket clientSocket, Server server, int clientId) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.clientId = clientId;
    }

    /**
     * 클라이언트 활성 상태 설정
     * @param isActive 클라이언트 활성 여부
     */
    public void setActive(boolean isActive) {
        this.isActive = isActive; // 활성 상태가 true인 경우만 게임에 참여 가능
    }

    /**
     * 클라이언트에게 메시지 전송
     * @param message 클라이언트에게 전송할 메시지
     */
    public void sendMessage(String message) {
        try {
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true); // 클라이언트로 데이터를 보내는 출력 스트림
            writer.println(message); // 메시지 전송
        } catch (IOException e) {
            System.err.println("클라이언트 [" + clientId + "] 메시지 전송 오류: " + e.getMessage());
        }
    }

    /**
     * 클라이언트 연결 닫기
     */
    public void closeConnection() {
        try {
            clientSocket.close(); // 클라이언트 소켓 닫기
        } catch (IOException e) {
            System.err.println("클라이언트 [" + clientId + "] 연결 닫기 오류: " + e.getMessage());
        }
    }

 
    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // 클라이언트에서 메시지 읽을 스트림 생성
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true) // 클라이언트로 메시지 보낼 스트림 생성
        ) {
            // 닉네임 요청하고 저장하는 작업
            writer.println("닉네임을 입력하세요:"); // 닉네임 입력하라고 메시지 보냄
            String nickname = reader.readLine(); // 클라이언트가 보낸 닉네임 읽음
            server.saveNickname(nickname); // 서버에 닉네임 저장
            writer.println("환영합니다, " + nickname + "!"); // 닉네임 환영 메시지 보냄
    
            // 클라이언트 메시지 처리 메서드 호출
            processClientMessages(reader, writer, nickname); // 송수신 로직 따로 처리
        } catch (IOException e) {
            System.err.println("클라이언트 처리 오류: " + e.getMessage()); // 에러 있으면 로그 찍음
        } finally {
            closeConnection(); // 소켓 닫음
            server.removeClient(this); // 클라이언트를 서버에서 제거
        }
    }
    
    /**
     * 클라이언트 메시지 처리
     * - 메시지 수신하고 서버 응답을 보냄
     * @param reader 클라이언트 입력
     * @param writer 클라이언트로 메시지 보냄
     * @param nickname 클라이언트 닉네임
     */
    private void processClientMessages(BufferedReader reader, PrintWriter writer, String nickname) {
        try {
            String clientMessage; // 클라이언트 메시지 저장할 변수
            while ((clientMessage = reader.readLine()) != null) { // 클라이언트 메시지 계속 읽음
                System.out.println("클라이언트 [" + nickname + "] 메시지: " + clientMessage); // 서버 로그로 메시지 찍음
    
                // 'exit' 명령 처리
                if ("exit".equalsIgnoreCase(clientMessage)) { // 클라이언트가 'exit' 보냈는지 확인
                    writer.println("서버에서 연결을 종료합니다."); // 종료 메시지 보냄
                    System.out.println("클라이언트 [" + nickname + "] 연결 종료 요청"); // 서버 로그 출력
                    break; // 반복문 나감
                }
    
                // 받은 메시지를 에코로 그대로 응답
                writer.println("서버 응답: " + clientMessage); // 에코 메시지 보냄
            }
        } catch (IOException e) {
            System.err.println("클라이언트 메시지 처리 오류: " + e.getMessage()); // 메시지 처리 중 에러 있으면 찍음
        }
    }
    
    
}
