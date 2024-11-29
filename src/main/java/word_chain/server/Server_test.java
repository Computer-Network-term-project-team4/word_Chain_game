package word_chain.server;

import word_chain.gameLogic.GameScore;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server_test {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_TURNS = 5; // 게임 종료 조건: 총 턴 수
    private final List<ClientHandler_test> clients = new ArrayList<>();
    private final GameScore gameScore = new GameScore();
    private boolean isRunning = true;
    private int currentPlayerIndex = 0; // 현재 차례의 플레이어 인덱스
    private int remainingTurns = MAX_TURNS;
    private String lastWord = null; // 마지막 단어
    private char startingLetter; // 랜덤 시작 알파벳
    private Timer turnTimer; //타이머

    public static void main(String[] args) {
        new Server_test().startServer();
    }

    public synchronized void broadcast(String message) {
        for (ClientHandler_test client : clients) {
            client.sendMessage(message);
        }
    }

    public synchronized void removeClient(ClientHandler_test client) {
        int removedIndex = clients.indexOf(client);
        clients.remove(client);
        broadcast(client.getNickname() + " 님이 나갔습니다.");

        // Adjust currentPlayerIndex if necessary
        if (removedIndex < currentPlayerIndex) {
            currentPlayerIndex--;
        } else if (removedIndex == currentPlayerIndex) {
            // The current player left, advance to the next player
            if (clients.size() > 0) {
                currentPlayerIndex = currentPlayerIndex % clients.size();
                nextTurn();
            } else {
                // No more players left; end the game
                endGame();
            }
        }

        updateClientStates();
    }

    public synchronized void startGame() {
        broadcast("모든 유저가 접속했습니다! 게임이 시작됩니다.");
        startingLetter = (char) ('a' + new Random().nextInt(26));
        broadcast("첫 번째 단어는 '" + startingLetter + "'로 시작해야 합니다.");
        nextTurn();
    }

    private synchronized void nextTurn() {
        if (remainingTurns == 0) {
            endGame();
            return;
        }

        if (clients.isEmpty()) {
            broadcast("모든 플레이어가 나갔습니다. 게임을 종료합니다.");
            endGame();
            return;
        }

        ClientHandler_test currentPlayer = clients.get(currentPlayerIndex);
        broadcast("현재 차례: " + currentPlayer.getNickname());
        currentPlayer.sendMessage("당신의 차례입니다! 단어를 입력하세요.");
        
        //타이머
        if (turnTimer != null) {
        	turnTimer.cancel();
        }
        turnTimer = new Timer();
        turnTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (Server_test.this) {
                    broadcast(currentPlayer.getNickname() + " 님이 시간을 초과했습니다! 다음 차례로 넘어갑니다.");
                    currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
                    remainingTurns--;
                    nextTurn();
                }
            }
        }, 30000); // 30초 제한 시간
    }

    private synchronized void endGame() {
        broadcast("게임이 종료되었습니다!");
        List<ClientHandler_test> sortedClients = new ArrayList<>(clients);
        sortedClients.sort(Comparator.comparingInt(ClientHandler_test::getScore).reversed());

        for (int rank = 0; rank < sortedClients.size(); rank++) {
            ClientHandler_test client = sortedClients.get(rank);
            client.sendMessage("게임 종료! 당신의 점수: " + client.getScore() + ", 순위: " + (rank + 1));
        }
        
        if (turnTimer != null) {
        	turnTimer.cancel();
        }
    }

    public synchronized void processWord(String word, ClientHandler_test player) {
        if (clients.indexOf(player) != currentPlayerIndex) {
            player.sendMessage("현재 당신의 차례가 아닙니다.");
            return;
        }

        String response;
        if (lastWord == null) {
            response = gameScore.processFirstWord(word, startingLetter);
        } else {
            response = gameScore.processNextWord(word, lastWord);
        }

        if (response.startsWith("유효한 단어")) {
            lastWord = word;
            int points = Integer.parseInt(response.split(": ")[1]);
            player.addScore(points);
            
            //타이머 리셋
            if (turnTimer !=null) {
            	turnTimer.cancel();
            }
        }

        player.sendMessage(response);

        // Update the currentPlayerIndex and remainingTurns here
        currentPlayerIndex = (currentPlayerIndex + 1) % clients.size();
        remainingTurns--;

        nextTurn();
    }

    public synchronized void updateClientStates() {
        boolean allReady = clients.size() == MAX_PLAYERS;

        for (ClientHandler_test client : clients) {
            if (allReady) {
                client.sendMessage("게임에 참여 중입니다!");
            } else {
                client.sendMessage("대기 중입니다.");
            }
        }

        if (allReady) {
            startGame();
        }
    }

    public void startServer() {
        System.out.println("서버가 시작되었습니다. 포트: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler_test clientHandler = new ClientHandler_test(clientSocket, this);

                synchronized (this) {
                    clients.add(clientHandler);
                }

                new Thread(clientHandler).start();
                updateClientStates();
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        } finally {
            shutdownServer();
        }
    }

    private void shutdownServer() {
        broadcast("서버가 종료됩니다.");
        if (turnTimer != null) {
        	turnTimer.cancel();
        }
        for (ClientHandler_test client : clients) {
            client.closeConnection();
        }
    }
}
