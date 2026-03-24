import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    private static final String DEFAULT_SERVER_HOST = "localhost";
    private static final int DEFAULT_SERVER_PORT = 8080;

    public static void main(String[] args) {
        String serverHost = args.length > 0 ? args[0] : DEFAULT_SERVER_HOST;
        int serverPort = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SERVER_PORT;

        try (
                Socket socket = new Socket(serverHost, serverPort);
                BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader keyboardReader = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Đã kết nối tới server " + serverHost + ":" + serverPort);
            System.out.println("Hãy nhập tên người dùng khi server yêu cầu.");

            // Luồng nhận dữ liệu từ server
            Thread receiveThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = serverReader.readLine()) != null) {
                        System.out.println(serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("Mất kết nối tới server.");
                }
            });

            receiveThread.start();

            // Luồng chính đọc dữ liệu từ bàn phím và gửi đi
            String userInput;
            while ((userInput = keyboardReader.readLine()) != null) {
                writer.println(userInput);

                if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")
                        || userInput.equalsIgnoreCase("/quit") || userInput.equalsIgnoreCase("/exit")) {
                    break;
                }
            }

            try {
                receiveThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("Client đã đóng.");
        } catch (IOException e) {
            System.out.println("Không thể kết nối tới server: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Cổng không hợp lệ. Ví dụ chạy: java ChatClient localhost 8080");
        }
    }
}