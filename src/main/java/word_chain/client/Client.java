package word_chain.client;

import java.io.*;
import java.net.*;

/**
 * 클라이언트 프로그램
 * - 서버에 연결하여 사용자 입력을 전송하고 서버로부터 응답을 수신
 */
public class Client {
    private static final String SERVER_ADDRESS = "localhost"; // 서버 주소 (현재는 로컬 PC로 연결)
    private static final int SERVER_PORT = 12345; // 서버가 수신 대기 중인 포트 번호

    public static void main(String[] args) {
        // 서버와 연결 시도
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) { // 서버 소켓 생성
            System.out.println("서버에 연결되었습니다!");

            // 서버와 클라이언트 간 데이터 송수신을 위한 스트림 생성
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 서버에서 오는 데이터 읽기
            PrintWriter serverWriter = new PrintWriter(socket.getOutputStream(), true); // 서버로 데이터 전송
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in)); // 사용자 입력 처리

            // 서버에서 초기 메시지 수신 및 출력
            String serverMessage = serverReader.readLine(); // 서버로부터 메시지를 읽음
            System.out.println("서버 메시지: " + serverMessage);

            // 사용자 입력을 서버로 전송하고 서버 응답을 출력
            String userMessage;
            System.out.println("단어를 입력하세요 (종료하려면 'exit' 입력):");
            while ((userMessage = userInput.readLine()) != null) { // 콘솔에서 사용자 입력 대기
                serverWriter.println(userMessage); // 입력된 메시지를 서버로 전송

                // 사용자가 'exit' 입력 시 프로그램 종료
                if ("exit".equalsIgnoreCase(userMessage)) {
                    System.out.println("연결을 종료합니다.");
                    break;
                }

                // 서버의 응답을 수신하여 출력
                serverMessage = serverReader.readLine(); // 서버로부터 응답 읽기
                System.out.println("서버 응답: " + serverMessage);
            }
        } catch (IOException e) {
            // 서버 연결 실패 또는 통신 오류 처리
            System.err.println("서버에 연결할 수 없습니다: " + e.getMessage());
        }
    }
}
