package network;

import java.io.*;
import java.net.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost"; // 서버 주소 (동일 PC는 localhost)
    private static final int SERVER_PORT = 12345; // 서버 포트 번호

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) { // 서버 연결
            System.out.println("서버에 연결되었습니다!");

            // 서버와 통신을 위한 스트림 생성
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter serverWriter = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

            // 서버로부터 초기 메시지 출력
            String serverMessage = serverReader.readLine();
            System.out.println("서버 메시지: " + serverMessage);

            // 사용자 입력 및 서버와 데이터 송수신
            String userMessage;
            System.out.println("단어를 입력하세요 (종료하려면 'exit' 입력):");
            while ((userMessage = userInput.readLine()) != null) {
                serverWriter.println(userMessage); // 사용자 입력을 서버에 전송

                if ("exit".equalsIgnoreCase(userMessage)) { // 종료 명령 처리
                    System.out.println("연결을 종료합니다.");
                    break;
                }

                // 서버로부터의 응답 출력
                serverMessage = serverReader.readLine();
                System.out.println("서버 응답: " + serverMessage);
            }
        } catch (IOException e) {
            System.err.println("서버에 연결할 수 없습니다: " + e.getMessage());
        }
    }
}
