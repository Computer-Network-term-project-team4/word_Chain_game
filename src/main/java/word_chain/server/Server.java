package word_chain.server;

import word_chain.client.ClientHandler;

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 12345; // 서버가 수신 대기할 포트 번호
    private static final int MAX_PLAYERS = 4; // 최대 활성 플레이어 수
    private int clientIdCounter = 1; // 클라이언트 고유 ID 카운터 (연결 순번)
    private List<ClientHandler> clients = new ArrayList<>(); // 연결된 클라이언트 목록
    private boolean isRunning = true; // 서버 실행 상태 플래그

    public static void main(String[] args) {
        new Server().startServer(); // 서버 시작
    }

    public void startServer() {
        System.out.println("서버가 시작되었습니다. 포트: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) { // 지정된 포트로 서버 소켓 생성
            // 별도 스레드로 콘솔 입력 감시
            new Thread(() -> monitorConsoleInput(serverSocket)).start();

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept(); // 클라이언트 연결 요청 수락
                    int clientId = clientIdCounter++; // 고유 ID 부여
                    System.out.println("클라이언트 연결됨: " + clientSocket.getInetAddress());
                    System.out.println("클라이언트 순번: " + clientId);

                    // 새 클라이언트 생성 및 목록에 추가
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, clientId);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start(); // 클라이언트를 개별 스레드로 처리

                    updateClientStates(); // 활성/대기 상태 업데이트
                } catch (SocketException e) {
                    if (!isRunning) break; // 서버 종료 중이라면 루프 탈출
                    System.err.println("소켓 오류: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        } finally {
            shutdownServer();
        }
    }

    // 콘솔 입력 감시
    private void monitorConsoleInput(ServerSocket serverSocket) {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                String command = consoleReader.readLine();
                if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("서버 종료 명령 감지. 서버를 종료합니다...");
                    isRunning = false; // 서버 실행 상태 종료
                    serverSocket.close(); // 서버 소켓 닫기
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("콘솔 입력 감시 오류: " + e.getMessage());
        }
    }

    // 활성/대기 상태를 업데이트
    private synchronized void updateClientStates() {
        System.out.println("현재 연결된 클라이언트 수: " + clients.size());
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            if (i < MAX_PLAYERS) {
                client.setActive(true); // 활성 상태로 설정
                client.sendMessage("게임에 참여 중입니다!");
            } else {
                client.setActive(false); // 대기 상태로 설정
                client.sendMessage("대기 중입니다. 게임이 시작되면 알림을 받습니다.");
            }
        }
    }

    // 연결 종료 시 호출되어 클라이언트 목록에서 제거
    public synchronized void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        System.out.println("클라이언트가 연결을 종료했습니다.");
        updateClientStates(); // 연결 종료 시 상태 업데이트
    }

    // 서버 종료: 모든 클라이언트 연결 닫기
    private void shutdownServer() {
        System.out.println("서버를 종료합니다...");
        for (ClientHandler client : clients) {
            client.closeConnection(); // 클라이언트 연결 닫기
        }
        clients.clear();
        System.out.println("모든 클라이언트 연결이 종료되었습니다.");
    }
}