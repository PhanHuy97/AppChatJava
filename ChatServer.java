import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int MIN_GROUP_MEMBERS = 2;
    private static final int GROUP_CHECK_DELAY_SECONDS = 30;

    private static final ConcurrentMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ChatGroup> groups = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Cổng không hợp lệ, hệ thống sẽ dùng cổng mặc định 8080.");
            }
        }

        System.out.println("MiniChat Server đang chạy tại cổng " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Không thể khởi động server: " + e.getMessage());
        } finally {
            scheduler.shutdown();
        }
    }

    // Lớp lưu thông tin của một nhóm chat
    private static class ChatGroup {
        private final String name;
        private final String password;
        private final Set<String> members;

        public ChatGroup(String name, String password) {
            this.name = name;
            this.password = password == null ? "" : password;
            this.members = ConcurrentHashMap.newKeySet();
        }

        public String getName() {
            return name;
        }

        public String getPassword() {
            return password;
        }

        public Set<String> getMembers() {
            return members;
        }

        public boolean isPrivateGroup() {
            return !password.isEmpty();
        }
    }

    // Luồng xử lý riêng cho từng client
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);

                if (!login()) {
                    return;
                }

                send("Đăng nhập thành công với tên: " + username);
                sendHelp();
                broadcastSystemMessage(username + " vừa kết nối vào phòng chat.", username);

                String input;
                while ((input = reader.readLine()) != null) {
                    input = input.trim();

                    if (input.isEmpty()) {
                        continue;
                    }

                    if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")
                            || input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/exit")) {
                        send("Bạn đã ngắt kết nối khỏi server.");
                        break;
                    }

                    if (input.startsWith("/")) {
                        handleCommand(input);
                    } else {
                        handleChatMessage(input);
                    }
                }
            } catch (IOException e) {
                System.out.println("Mất kết nối với client: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        // Xử lý đăng nhập và kiểm tra username không bị trùng
        private boolean login() throws IOException {
            while (true) {
                send("Nhập tên người dùng:");
                String name = reader.readLine();

                if (name == null) {
                    return false;
                }

                name = name.trim();

                if (name.isEmpty()) {
                    send("Tên người dùng không được để trống.");
                    continue;
                }

                if (name.contains(" ")) {
                    send("Tên người dùng không được chứa khoảng trắng.");
                    continue;
                }

                ClientHandler existing = clients.putIfAbsent(name, this);
                if (existing == null) {
                    username = name;
                    return true;
                }

                send("Tên người dùng đã tồn tại, vui lòng nhập tên khác.");
            }
        }

        // Phân loại tin nhắn người dùng gửi lên
        private void handleChatMessage(String message) {
            if (message.startsWith("@")) {
                sendPrivateMessage(message);
            } else if (message.startsWith("#")) {
                sendGroupMessage(message);
            } else {
                broadcastToAll(username + " (all): " + message);
            }
        }

        // Gửi tin nhắn riêng
        private void sendPrivateMessage(String message) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length < 2 || parts[0].length() == 1) {
                send("Sai cú pháp. Ví dụ: @tenNguoiDung Xin chào");
                return;
            }

            String targetUser = parts[0].substring(1);
            String content = parts[1].trim();
            ClientHandler targetClient = clients.get(targetUser);

            if (targetClient == null) {
                send("Không tìm thấy người dùng: " + targetUser);
                return;
            }

            targetClient.send("[Tin nhắn riêng] " + username + ": " + content);
            if (!targetUser.equals(username)) {
                send("[Bạn -> " + targetUser + "] " + content);
            }
        }

        // Gửi tin nhắn vào nhóm
        private void sendGroupMessage(String message) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length < 2 || parts[0].length() == 1) {
                send("Sai cú pháp. Ví dụ: #tenNhom Noi dung can gui");
                return;
            }

            String groupName = parts[0].substring(1);
            String content = parts[1].trim();
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Nhóm không tồn tại: " + groupName);
                return;
            }

            if (!group.getMembers().contains(username)) {
                send("Bạn chưa tham gia nhóm: " + groupName);
                return;
            }

            for (String memberName : group.getMembers()) {
                ClientHandler member = clients.get(memberName);
                if (member != null) {
                    member.send(username + " (" + groupName + "): " + content);
                }
            }
        }

        // Xử lý lệnh bắt đầu bằng dấu /
        private void handleCommand(String command) {
            String[] parts = command.trim().split("\\s+");
            String action = parts[0].toLowerCase();

            switch (action) {
                case "/help":
                    sendHelp();
                    break;
                case "/users":
                    showUsers();
                    break;
                case "/groups":
                    showGroups();
                    break;
                case "/mygroups":
                    showMyGroups();
                    break;
                case "/creategroup":
                    createGroup(command);
                    break;
                case "/join":
                    joinGroup(command);
                    break;
                case "/leave":
                    leaveGroup(parts);
                    break;
                case "/invite":
                    inviteUser(parts);
                    break;
                case "/members":
                    showMembers(parts);
                    break;
                default:
                    send("Lệnh không hợp lệ. Gõ /help để xem danh sách lệnh.");
                    break;
            }
        }

        // Hiển thị hướng dẫn sử dụng
        private void sendHelp() {
            send("===== DANH SÁCH LỆNH =====");
            send("/help                         : Xem hướng dẫn");
            send("/users                        : Xem danh sách người dùng đang online");
            send("/groups                       : Xem danh sách nhóm hiện có");
            send("/mygroups                     : Xem các nhóm bạn đang tham gia");
            send("/createGroup tenNhom [mk]     : Tạo nhóm mới, mật khẩu là tùy chọn");
            send("/join tenNhom [mk]            : Tham gia nhóm");
            send("/leave tenNhom                : Rời khỏi nhóm");
            send("/invite tenNhom tenNguoiDung  : Mời user vào nhóm");
            send("/members tenNhom              : Xem thành viên của nhóm");
            send("@tenNguoiDung noiDung         : Gửi tin nhắn riêng");
            send("#tenNhom noiDung              : Gửi tin nhắn vào nhóm");
            send("noiDung                       : Gửi tin nhắn cho tất cả mọi người");
            send("exit hoặc /quit               : Thoát chương trình");
            send("=============================");
        }

        // Hiển thị người dùng đang online
        private void showUsers() {
            List<String> names = new ArrayList<>(clients.keySet());
            Collections.sort(names);
            send("Người dùng đang online: " + String.join(", ", names));
        }

        // Hiển thị danh sách nhóm
        private void showGroups() {
            if (groups.isEmpty()) {
                send("Hiện chưa có nhóm nào được tạo.");
                return;
            }

            List<String> info = new ArrayList<>();
            for (ChatGroup group : groups.values()) {
                String label = group.getName();
                if (group.isPrivateGroup()) {
                    label += " (private)";
                }
                label += " - " + group.getMembers().size() + " thành viên";
                info.add(label);
            }
            Collections.sort(info);
            send("Danh sách nhóm: " + String.join(" | ", info));
        }

        // Hiển thị các nhóm mà người dùng đang tham gia
        private void showMyGroups() {
            List<String> myGroups = new ArrayList<>();
            for (ChatGroup group : groups.values()) {
                if (group.getMembers().contains(username)) {
                    myGroups.add(group.getName());
                }
            }

            if (myGroups.isEmpty()) {
                send("Bạn chưa tham gia nhóm nào.");
                return;
            }

            Collections.sort(myGroups);
            send("Các nhóm của bạn: " + String.join(", ", myGroups));
        }

        // Tạo nhóm mới
        private void createGroup(String command) {
            String[] parts = command.split("\\s+", 3);
            if (parts.length < 2) {
                send("Sai cú pháp. Ví dụ: /createGroup javaTeam 123");
                return;
            }

            String groupName = parts[1];
            String password = parts.length == 3 ? parts[2] : "";

            ChatGroup newGroup = new ChatGroup(groupName, password);
            newGroup.getMembers().add(username);

            ChatGroup oldGroup = groups.putIfAbsent(groupName, newGroup);
            if (oldGroup != null) {
                send("Nhóm đã tồn tại: " + groupName);
                return;
            }

            send("Tạo nhóm thành công: " + groupName);
            send("Bạn đã được thêm vào nhóm ngay sau khi tạo.");
            send("Lưu ý: sau 30 giây, nếu nhóm vẫn dưới 2 thành viên thì hệ thống sẽ tự xóa nhóm.");

            scheduler.schedule(() -> {
                ChatGroup currentGroup = groups.get(groupName);
                if (currentGroup != null && currentGroup.getMembers().size() < MIN_GROUP_MEMBERS) {
                    groups.remove(groupName);
                    notifyGroupMembers(currentGroup, "Nhóm " + groupName + " đã bị xóa do không đủ thành viên.");
                }
            }, GROUP_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
        }

        // Tham gia nhóm
        private void joinGroup(String command) {
            String[] parts = command.split("\\s+", 3);
            if (parts.length < 2) {
                send("Sai cú pháp. Ví dụ: /join javaTeam 123");
                return;
            }

            String groupName = parts[1];
            String password = parts.length == 3 ? parts[2] : "";
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Nhóm không tồn tại: " + groupName);
                return;
            }

            if (group.getMembers().contains(username)) {
                send("Bạn đã là thành viên của nhóm " + groupName);
                return;
            }

            if (!group.getPassword().equals(password)) {
                send("Mật khẩu nhóm không đúng.");
                return;
            }

            group.getMembers().add(username);
            send("Tham gia nhóm thành công: " + groupName);
            notifyGroupMembers(group, username + " vừa tham gia nhóm " + groupName + ".");
        }

        // Rời khỏi nhóm
        private void leaveGroup(String[] parts) {
            if (parts.length < 2) {
                send("Sai cú pháp. Ví dụ: /leave javaTeam");
                return;
            }

            String groupName = parts[1];
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Nhóm không tồn tại: " + groupName);
                return;
            }

            if (!group.getMembers().remove(username)) {
                send("Bạn không nằm trong nhóm " + groupName);
                return;
            }

            send("Bạn đã rời khỏi nhóm: " + groupName);
            notifyGroupMembers(group, username + " vừa rời khỏi nhóm " + groupName + ".");

            if (group.getMembers().isEmpty()) {
                groups.remove(groupName);
                send("Nhóm " + groupName + " đã được xóa vì không còn thành viên nào.");
            }
        }

        // Mời người dùng khác vào nhóm
        private void inviteUser(String[] parts) {
            if (parts.length < 3) {
                send("Sai cú pháp. Ví dụ: /invite javaTeam an");
                return;
            }

            String groupName = parts[1];
            String invitee = parts[2];
            ChatGroup group = groups.get(groupName);
            ClientHandler invitedClient = clients.get(invitee);

            if (group == null) {
                send("Nhóm không tồn tại: " + groupName);
                return;
            }

            if (!group.getMembers().contains(username)) {
                send("Bạn phải là thành viên của nhóm mới có thể mời người khác.");
                return;
            }

            if (invitedClient == null) {
                send("Không tìm thấy người dùng: " + invitee);
                return;
            }

            if (!group.getMembers().add(invitee)) {
                send(invitee + " đã ở trong nhóm " + groupName);
                return;
            }

            send("Đã thêm " + invitee + " vào nhóm " + groupName);
            invitedClient.send("Bạn được " + username + " mời vào nhóm " + groupName);
            notifyGroupMembers(group, invitee + " vừa được thêm vào nhóm " + groupName + ".");
        }

        // Hiển thị thành viên của một nhóm
        private void showMembers(String[] parts) {
            if (parts.length < 2) {
                send("Sai cú pháp. Ví dụ: /members javaTeam");
                return;
            }

            String groupName = parts[1];
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Nhóm không tồn tại: " + groupName);
                return;
            }

            List<String> members = new ArrayList<>(group.getMembers());
            Collections.sort(members);
            send("Thành viên nhóm " + groupName + ": " + String.join(", ", members));
        }

        // Gửi dữ liệu về client hiện tại
        private synchronized void send(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        // Ngắt kết nối và dọn dẹp dữ liệu
        private void disconnect() {
            if (username != null) {
                clients.remove(username);
                removeUserFromAllGroups(username);
                broadcastSystemMessage(username + " đã rời khỏi phòng chat.", username);
            }

            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                System.out.println("Lỗi khi đóng reader: " + e.getMessage());
            }

            if (writer != null) {
                writer.close();
            }

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.out.println("Lỗi khi đóng socket: " + e.getMessage());
            }
        }
    }

    // Gửi tin nhắn cho tất cả client
    private static void broadcastToAll(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
        System.out.println(message);
    }

    // Gửi thông báo hệ thống
    private static void broadcastSystemMessage(String message, String excludedUser) {
        String systemMessage = "[Hệ thống] " + message;
        for (ClientHandler client : clients.values()) {
            if (excludedUser == null || !client.username.equals(excludedUser)) {
                client.send(systemMessage);
            }
        }
        System.out.println(systemMessage);
    }

    // Gửi thông báo cho các thành viên trong nhóm
    private static void notifyGroupMembers(ChatGroup group, String message) {
        for (String memberName : group.getMembers()) {
            ClientHandler member = clients.get(memberName);
            if (member != null) {
                member.send("[Nhóm " + group.getName() + "] " + message);
            }
        }
    }

    // Xóa user khỏi tất cả nhóm khi ngắt kết nối
    private static void removeUserFromAllGroups(String username) {
        List<String> emptyGroups = new ArrayList<>();

        for (ChatGroup group : groups.values()) {
            if (group.getMembers().remove(username)) {
                notifyGroupMembers(group, username + " đã thoát khỏi nhóm " + group.getName() + ".");
            }
            if (group.getMembers().isEmpty()) {
                emptyGroups.add(group.getName());
            }
        }

        for (String groupName : emptyGroups) {
            groups.remove(groupName);
        }
    }
}