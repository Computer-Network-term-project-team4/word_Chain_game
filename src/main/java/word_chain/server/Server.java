package word_chain.server;

import word_chain.client.ClientHandler;

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 서버 프로그램
 * - 클라이언트의 연결 요청을 수락하고, 연결된 클라이언트를 관리하며, 게임 상태를 조정
 */
public class Server {
    private static final int PORT = 12345; // 서버가 수신 대기할 포트 번호
    private static final int MAX_PLAYERS = 4; // 활성 클라이언트(플레이어) 최대 수
    private int clientIdCounter = 1; // 클라이언트 고유 ID 부여용 카운터
    private List<ClientHandler> clients = new ArrayList<>(); // 연결된 클라이언트 목록
    private boolean isRunning = true; // 서버 실행 상태

    public static void main(String[] args) {
        new Server().startServer(); // 서버 실행
    }

    /**
     * 서버 시작
     * - 클라이언트의 연결 요청을 수락하며, 각 클라이언트를 개별 스레드로 처리
     */
    public void startServer() {
        System.out.println("서버가 시작되었습니다. 포트: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) { // 지정된 포트로 서버 소켓 생성
            new Thread(() -> monitorConsoleInput(serverSocket)).start(); // 콘솔 입력(서버 종료 명령) 감시

            while (isRunning) { // 서버가 실행 중인 동안 클라이언트 요청 수락
                try {
                    Socket clientSocket = serverSocket.accept(); // 클라이언트 연결 요청 수락
                    int clientId = clientIdCounter++; // 클라이언트 고유 ID 부여
                    System.out.println("클라이언트 연결됨: " + clientSocket.getInetAddress());
                    System.out.println("클라이언트 순번: " + clientId);

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, clientId); // 클라이언트 핸들러 생성
                    clients.add(clientHandler); // 클라이언트를 목록에 추가
                    new Thread(clientHandler).start(); // 클라이언트를 개별 스레드로 실행

                    updateClientStates(); // 클라이언트 상태 업데이트 (활성/대기)
                } catch (SocketException e) {
                    if (!isRunning) break; // 서버 종료 상태면 루프 탈출
                    System.err.println("소켓 오류: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        } finally {
            shutdownServer(); // 서버 종료 시 모든 클라이언트 연결 닫기
        }
    }

    /**
     * 클라이언트 상태 업데이트
     * - 활성 클라이언트와 대기 클라이언트를 구분하여 상태를 설정
     */
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

    /**
     * 콘솔 입력 감시
     * - 서버 종료 명령("exit")을 처리합니다.
     */
    private void monitorConsoleInput(ServerSocket serverSocket) {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                String command = consoleReader.readLine();
                if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("서버 종료 명령 감지. 서버를 종료합니다...");
                    isRunning = false;
                    serverSocket.close(); // 서버 소켓 닫기
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("콘솔 입력 감시 오류: " + e.getMessage());
        }
    }

    /**
     * 클라이언트 제거
     * - 연결이 종료된 클라이언트를 목록에서 제거
     */
    public synchronized void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        System.out.println("클라이언트가 연결을 종료했습니다.");
        updateClientStates(); // 상태 업데이트
    }

    /**
     * 서버 종료
     * - 모든 클라이언트 연결을 종료
     */
    private void shutdownServer() {
        System.out.println("서버를 종료합니다...");
        for (ClientHandler client : clients) {
            client.closeConnection(); // 클라이언트 연결 닫기
        }
        clients.clear(); // 클라이언트 목록 초기화
        System.out.println("모든 클라이언트 연결이 종료되었습니다.");
    }
}
