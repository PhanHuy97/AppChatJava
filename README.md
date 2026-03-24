# MiniChat Java Application

A simple client-server chat application built with Java Socket programming. This project contains two main files:

* `ChatServer.java`
* `ChatClient.java`

The application demonstrates the basic concepts of network programming, multi-client communication, private messaging, and group chat handling in Java.

## Features

* Multi-client chat using Java sockets
* Broadcast messages to all connected users
* Private messaging between users
* Group chat support
* Create, join, and leave groups
* View online users and available groups
* Simple console-based interface

## Project Structure

```text
.
├── ChatServer.java
└── ChatClient.java
```

## Requirements

Before running the project, make sure you have:

* Java JDK 17 or later installed
* A terminal / command prompt
* Any Java IDE (optional), such as Eclipse or IntelliJ IDEA

## How It Works

### Server

`ChatServer.java` is responsible for:

* Opening a server socket
* Accepting connections from multiple clients
* Managing connected usernames
* Handling public, private, and group messages
* Managing chat groups

### Client

`ChatClient.java` is responsible for:

* Connecting to the server
* Sending messages from the keyboard
* Receiving messages from the server in real time
* Displaying all chat activity in the console

## Compile the Project

Open a terminal in the project folder and run:

```bash
javac ChatServer.java ChatClient.java
```

## Run the Server

Start the server first:

```bash
java ChatServer
```

To run the server on a custom port:

```bash
java ChatServer 9090
```

## Run the Client

Open a new terminal window for each client and run:

```bash
java ChatClient
```

To connect to a custom host and port:

```bash
java ChatClient localhost 9090
```

## Basic Commands

After connecting, the server will ask for a username.

Use the following commands in the client:

* Send a public message:

  ```text
  Hello everyone
  ```

* Send a private message:

  ```text
  @username Hello
  ```

* Send a group message:

  ```text
  #groupName Hello team
  ```

* Show help:

  ```text
  /help
  ```

* Show online users:

  ```text
  /users
  ```

* Show available groups:

  ```text
  /groups
  ```

* Show groups you joined:

  ```text
  /mygroups
  ```

* Create a group:

  ```text
  /createGroup groupName
  ```

* Create a private group with password:

  ```text
  /createGroup groupName 123
  ```

* Join a group:

  ```text
  /join groupName
  ```

* Join a password-protected group:

  ```text
  /join groupName 123
  ```

* Leave a group:

  ```text
  /leave groupName
  ```

* Invite a user into a group:

  ```text
  /invite groupName username
  ```

* View group members:

  ```text
  /members groupName
  ```

* Exit the client:

  ```text
  exit
  ```

  or

  ```text
  /quit
  ```

## Example Workflow

1. Start `ChatServer`
2. Run two or more `ChatClient` instances
3. Enter different usernames
4. Send public messages
5. Test private messages with `@username`
6. Create a group and send messages with `#groupName`

## Notes

* The project is console-based and designed for learning purposes.
* The server should always be started before clients connect.
* If two users try to use the same username, the server will ask one of them to choose another name.

## Future Improvements

Possible enhancements for this project:

* Graphical user interface (GUI)
* Message history storage
* File sharing
* User authentication
* Database integration
* End-to-end encryption

## Author

Developed as a Java client-server chat project for academic practice.

## License

This project is for educational use.
