package word_chain.client;

import word_chain.server.Server;

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

    /**
     * 클라이언트와의 통신 처리
     * - 클라이언트로부터 메시지를 수신하고, 활성 상태에 따라 응답을 전송
     */
    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // 클라이언트에서 오는 메시지 읽기
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true) // 클라이언트로 메시지 전송
        ) {
            // 초기 연결 메시지 전송
            writer.println("서버에 연결되었습니다! 당신의 순번은: " + clientId);

            String clientMessage;
            // 클라이언트 메시지를 반복적으로 수신 및 처리
            while ((clientMessage = reader.readLine()) != null) {
                if (isActive) { // 클라이언트가 활성 상태인 경우
                    System.out.println("활성 클라이언트 [" + clientId + "] 메시지: " + clientMessage);
                    writer.println("서버 응답: " + clientMessage); // 클라이언트에게 에코 메시지
                } else { // 클라이언트가 대기 상태인 경우
                    writer.println("대기 중입니다. 게임이 시작되면 알림을 받습니다.");
                }

                // 클라이언트가 'exit' 입력 시 연결 종료
                if ("exit".equalsIgnoreCase(clientMessage)) {
                    System.out.println("클라이언트 [" + clientId + "] 연결 종료 요청");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("클라이언트 [" + clientId + "] 처리 오류: " + e.getMessage());
        } finally {
            // 연결 종료 시 처리
            closeConnection(); // 클라이언트 소켓 닫기
            server.removeClient(this); // 서버에서 클라이언트 제거
        }
    }
}
