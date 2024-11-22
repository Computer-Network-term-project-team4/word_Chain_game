package word_chain.client;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private network.Server server; // 서버 인스턴스 참조
    private int clientId; // 클라이언트 고유 ID
    private boolean isActive = false; // 활성/대기 상태

    public ClientHandler(Socket clientSocket, network.Server server, int clientId) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.clientId = clientId;
    }

    // 활성/대기 상태를 설정하는 메서드
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    // 클라이언트에게 메시지 전송
    public void sendMessage(String message) {
        try {
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            writer.println(message);
        } catch (IOException e) {
            System.err.println("클라이언트 [" + clientId + "] 메시지 전송 오류: " + e.getMessage());
        }
    }

    // 클라이언트 연결 닫기
    public void closeConnection() {
        try {
            clientSocket.close(); // 클라이언트 소켓 닫기
        } catch (IOException e) {
            System.err.println("클라이언트 [" + clientId + "] 연결 닫기 오류: " + e.getMessage());
        }
    }

    // 클라이언트와의 통신을 처리하는 메인 메서드
    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            writer.println("서버에 연결되었습니다! 당신의 순번은: " + clientId);

            String clientMessage;
            while ((clientMessage = reader.readLine()) != null) {
                if (isActive) {
                    System.out.println("활성 클라이언트 [" + clientId + "] 메시지: " + clientMessage);
                    writer.println("서버 응답: " + clientMessage);
                } else {
                    writer.println("대기 중입니다. 게임이 시작되면 알림을 받습니다.");
                }

                if ("exit".equalsIgnoreCase(clientMessage)) {
                    System.out.println("클라이언트 [" + clientId + "] 연결 종료 요청");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("클라이언트 [" + clientId + "] 처리 오류: " + e.getMessage());
        } finally {
            closeConnection(); // 연결 닫기
            server.removeClient(this); // 서버에 연결 종료 알림
        }
    }
}