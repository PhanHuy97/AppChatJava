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
                System.out.println("Invalid port. The system will use the default port 8080.");
            }
        }

        System.out.println("MiniChat Server is running on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("Unable to start the server: " + e.getMessage());
        } finally {
            scheduler.shutdown();
        }
    }

    // Class for storing chat group information
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

    // Separate handler for each client
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

                send("Login successful with username: " + username);
                sendHelp();
                broadcastSystemMessage(username + " has joined the chat room.", username);

                String input;
                while ((input = reader.readLine()) != null) {
                    input = input.trim();

                    if (input.isEmpty()) {
                        continue;
                    }

                    if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")
                            || input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/exit")) {
                        send("You have disconnected from the server.");
                        break;
                    }

                    if (input.startsWith("/")) {
                        handleCommand(input);
                    } else {
                        handleChatMessage(input);
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost with client: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        // Handle login and check for duplicate username
        private boolean login() throws IOException {
            while (true) {
                send("Enter username:");
                String name = reader.readLine();

                if (name == null) {
                    return false;
                }

                name = name.trim();

                if (name.isEmpty()) {
                    send("Username must not be empty.");
                    continue;
                }

                if (name.contains(" ")) {
                    send("Username must not contain spaces.");
                    continue;
                }

                ClientHandler existing = clients.putIfAbsent(name, this);
                if (existing == null) {
                    username = name;
                    return true;
                }

                send("Username already exists. Please enter another one.");
            }
        }

        // Classify user messages
        private void handleChatMessage(String message) {
            if (message.startsWith("@")) {
                sendPrivateMessage(message);
            } else if (message.startsWith("#")) {
                sendGroupMessage(message);
            } else {
                broadcastToAll(username + " (all): " + message);
            }
        }

        // Send private message
        private void sendPrivateMessage(String message) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length < 2 || parts[0].length() == 1) {
                send("Invalid syntax. Example: @username Hello");
                return;
            }

            String targetUser = parts[0].substring(1);
            String content = parts[1].trim();
            ClientHandler targetClient = clients.get(targetUser);

            if (targetClient == null) {
                send("User not found: " + targetUser);
                return;
            }

            targetClient.send("[Private message] " + username + ": " + content);
            if (!targetUser.equals(username)) {
                send("[You -> " + targetUser + "] " + content);
            }
        }

        // Send message to group
        private void sendGroupMessage(String message) {
            String[] parts = message.split("\\s+", 2);
            if (parts.length < 2 || parts[0].length() == 1) {
                send("Invalid syntax. Example: #groupName message content");
                return;
            }

            String groupName = parts[0].substring(1);
            String content = parts[1].trim();
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Group does not exist: " + groupName);
                return;
            }

            if (!group.getMembers().contains(username)) {
                send("You have not joined the group: " + groupName);
                return;
            }

            for (String memberName : group.getMembers()) {
                ClientHandler member = clients.get(memberName);
                if (member != null) {
                    member.send(username + " (" + groupName + "): " + content);
                }
            }
        }

        // Handle commands starting with /
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
                    send("Invalid command. Type /help to see the list of commands.");
                    break;
            }
        }

        // Show user guide
        private void sendHelp() {
            send("===== COMMAND LIST =====");
            send("/help                         : Show help");
            send("/users                        : Show online users");
            send("/groups                       : Show existing groups");
            send("/mygroups                     : Show groups you have joined");
            send("/createGroup groupName [pwd]  : Create a new group, password is optional");
            send("/join groupName [pwd]         : Join a group");
            send("/leave groupName              : Leave a group");
            send("/invite groupName username    : Invite a user to a group");
            send("/members groupName            : Show group members");
            send("@username message             : Send a private message");
            send("#groupName message            : Send a message to a group");
            send("message                       : Send a message to everyone");
            send("exit or /quit                 : Exit the program");
            send("========================");
        }

        // Show online users
        private void showUsers() {
            List<String> names = new ArrayList<>(clients.keySet());
            Collections.sort(names);
            send("Online users: " + String.join(", ", names));
        }

        // Show groups
        private void showGroups() {
            if (groups.isEmpty()) {
                send("There are currently no groups.");
                return;
            }

            List<String> info = new ArrayList<>();
            for (ChatGroup group : groups.values()) {
                String label = group.getName();
                if (group.isPrivateGroup()) {
                    label += " (private)";
                }
                label += " - " + group.getMembers().size() + " members";
                info.add(label);
            }
            Collections.sort(info);
            send("Group list: " + String.join(" | ", info));
        }

        // Show groups the user has joined
        private void showMyGroups() {
            List<String> myGroups = new ArrayList<>();
            for (ChatGroup group : groups.values()) {
                if (group.getMembers().contains(username)) {
                    myGroups.add(group.getName());
                }
            }

            if (myGroups.isEmpty()) {
                send("You have not joined any groups.");
                return;
            }

            Collections.sort(myGroups);
            send("Your groups: " + String.join(", ", myGroups));
        }

        // Create a new group
        private void createGroup(String command) {
            String[] parts = command.split("\\s+", 3);
            if (parts.length < 2) {
                send("Invalid syntax. Example: /createGroup javaTeam 123");
                return;
            }

            String groupName = parts[1];
            String password = parts.length == 3 ? parts[2] : "";

            ChatGroup newGroup = new ChatGroup(groupName, password);
            newGroup.getMembers().add(username);

            ChatGroup oldGroup = groups.putIfAbsent(groupName, newGroup);
            if (oldGroup != null) {
                send("Group already exists: " + groupName);
                return;
            }

            send("Group created successfully: " + groupName);
            send("You have been added to the group immediately after creation.");
            send("Note: after 30 seconds, if the group still has fewer than 2 members, it will be deleted automatically.");

            scheduler.schedule(() -> {
                ChatGroup currentGroup = groups.get(groupName);
                if (currentGroup != null && currentGroup.getMembers().size() < MIN_GROUP_MEMBERS) {
                    groups.remove(groupName);
                    notifyGroupMembers(currentGroup, "Group " + groupName + " has been deleted due to insufficient members.");
                }
            }, GROUP_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
        }

        // Join a group
        private void joinGroup(String command) {
            String[] parts = command.split("\\s+", 3);
            if (parts.length < 2) {
                send("Invalid syntax. Example: /join javaTeam 123");
                return;
            }

            String groupName = parts[1];
            String password = parts.length == 3 ? parts[2] : "";
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Group does not exist: " + groupName);
                return;
            }

            if (group.getMembers().contains(username)) {
                send("You are already a member of group " + groupName);
                return;
            }

            if (!group.getPassword().equals(password)) {
                send("Incorrect group password.");
                return;
            }

            group.getMembers().add(username);
            send("Successfully joined group: " + groupName);
            notifyGroupMembers(group, username + " has joined group " + groupName + ".");
        }

        // Leave a group
        private void leaveGroup(String[] parts) {
            if (parts.length < 2) {
                send("Invalid syntax. Example: /leave javaTeam");
                return;
            }

            String groupName = parts[1];
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Group does not exist: " + groupName);
                return;
            }

            if (!group.getMembers().remove(username)) {
                send("You are not in group " + groupName);
                return;
            }

            send("You have left group: " + groupName);
            notifyGroupMembers(group, username + " has left group " + groupName + ".");

            if (group.getMembers().isEmpty()) {
                groups.remove(groupName);
                send("Group " + groupName + " has been deleted because it has no members left.");
            }
        }

        // Invite another user to a group
        private void inviteUser(String[] parts) {
            if (parts.length < 3) {
                send("Invalid syntax. Example: /invite javaTeam an");
                return;
            }

            String groupName = parts[1];
            String invitee = parts[2];
            ChatGroup group = groups.get(groupName);
            ClientHandler invitedClient = clients.get(invitee);

            if (group == null) {
                send("Group does not exist: " + groupName);
                return;
            }

            if (!group.getMembers().contains(username)) {
                send("You must be a member of the group to invite others.");
                return;
            }

            if (invitedClient == null) {
                send("User not found: " + invitee);
                return;
            }

            if (!group.getMembers().add(invitee)) {
                send(invitee + " is already in group " + groupName);
                return;
            }

            send("Added " + invitee + " to group " + groupName);
            invitedClient.send("You have been invited by " + username + " to group " + groupName);
            notifyGroupMembers(group, invitee + " has been added to group " + groupName + ".");
        }

        // Show members of a group
        private void showMembers(String[] parts) {
            if (parts.length < 2) {
                send("Invalid syntax. Example: /members javaTeam");
                return;
            }

            String groupName = parts[1];
            ChatGroup group = groups.get(groupName);

            if (group == null) {
                send("Group does not exist: " + groupName);
                return;
            }

            List<String> members = new ArrayList<>(group.getMembers());
            Collections.sort(members);
            send("Members of group " + groupName + ": " + String.join(", ", members));
        }

        // Send data to current client
        private synchronized void send(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        // Disconnect and clean up
        private void disconnect() {
            if (username != null) {
                clients.remove(username);
                removeUserFromAllGroups(username);
                broadcastSystemMessage(username + " has left the chat room.", username);
            }

            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                System.out.println("Error while closing reader: " + e.getMessage());
            }

            if (writer != null) {
                writer.close();
            }

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.out.println("Error while closing socket: " + e.getMessage());
            }
        }
    }

    // Send message to all clients
    private static void broadcastToAll(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
        System.out.println(message);
    }

    // Send system message
    private static void broadcastSystemMessage(String message, String excludedUser) {
        String systemMessage = "[System] " + message;
        for (ClientHandler client : clients.values()) {
            if (excludedUser == null || !client.username.equals(excludedUser)) {
                client.send(systemMessage);
            }
        }
        System.out.println(systemMessage);
    }

    // Notify group members
    private static void notifyGroupMembers(ChatGroup group, String message) {
        for (String memberName : group.getMembers()) {
            ClientHandler member = clients.get(memberName);
            if (member != null) {
                member.send("[Group " + group.getName() + "] " + message);
            }
        }
    }

    // Remove user from all groups when disconnected
    private static void removeUserFromAllGroups(String username) {
        List<String> emptyGroups = new ArrayList<>();

        for (ChatGroup group : groups.values()) {
            if (group.getMembers().remove(username)) {
                notifyGroupMembers(group, username + " has left group " + group.getName() + ".");
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