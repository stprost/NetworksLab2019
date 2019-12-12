import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;


public class ServerSession extends Thread {
    private static final String PATH_TO_CLIENTS_INFO = "src/main/resources/ClientsInfo";
    private static final String ERR_SIGN = "Wrong login or password";
    private static final String END_MSG = "end";
    private Socket socket;
    private Server server;
    private BufferedReader in;
    private BufferedWriter out;
    private User user;
    private boolean connect;

    public ServerSession() {

    }

    public ServerSession(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        connect = true;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        start();
    }

    @Override
    public void run() {
        while (connect) {
            try {
                inMessageSwitch(in.readLine());
            } catch (IOException e) {
                this.downService();
            }
        }
    }

    private void send(String msg) {
        try {
            System.out.println("to client: " + msg);
            out.write(msg + "\n");
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private void inMessageSwitch(String msg) throws IOException {
        if (msg == null) {
            disconnect();
            return;
        }
        System.out.println("from client: " + msg);
        String[] strings = msg.split("\\s+");
        switch (strings[0]) {
            case "signin":
                signin(strings[1], strings[2]);
                send(END_MSG);
                break;
            case "signup":
                signup(strings[1], strings[2]);
                send(END_MSG);
                break;
            case "getinfo":
                getInfo();
                send(END_MSG);
                break;
            case "list":
                getList();
                send(END_MSG);
                break;
            case "transaction":
                transaction(Integer.parseInt(strings[1]), Integer.parseInt(strings[2]));
                send(END_MSG);
                break;
            case "put":
                user.setSize(user.getSize() + Integer.parseInt(strings[1]));
                updateUserSizeInfo(user);
                send("Complete");
                send(END_MSG);
                break;
            case "take":
                if (user.getSize() < Integer.parseInt(strings[1])) {
                    send("Not enough money");
                    send(END_MSG);
                } else {
                    user.setSize(user.getSize() + Integer.parseInt(strings[1]));
                    updateUserSizeInfo(user);
                    send("Complete");
                    send(END_MSG);
                }
                break;
            case "/exit":
                connect = false;
                break;
            default:
                send("Unknown command");
                send(END_MSG);
        }
    }

    private void transaction(int id, int size) throws IOException {
        if (user.getSize() < size) {
            send("Sorry, not enough money");
        }
        for (ServerSession s : server.getClientList()) {
            if (s.getUser().getId() == id) {
                User user = s.getUser();
                s.send("Input transaction from: " + user.getLogin() +
                        ", " + user.getId() +
                        "for " + size +
                        "\nAccept? (yes/no)");
                s.send(END_MSG);
                acceptTransaction(in.readLine(), s, size);
                return;
            }
        }
    }

    private void acceptTransaction(String msg, ServerSession s, int size) throws IOException{
        while (true) {
            switch (msg) {
                case "yes":
                    this.user.setSize(this.user.getSize() - size);
                    updateUserSizeInfo(this.user);
                    user.setSize(user.getSize() + size);
                    updateUserSizeInfo(user);
                    s.setUser(user);
                    send("Transaction completed");
                    send(END_MSG);
                    return;
                case "no":
                    send("Transaction denied");
                    send(END_MSG);
                    return;
                default:
                    s.send("Unknown command\nAccept? (yes/no)");

            }
        }
    }
    private void getList() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        for (String str : Files.readAllLines(Paths.get(PATH_TO_CLIENTS_INFO))) {
            String[] line = str.split("\\s+");
            stringBuilder.append("Login: ").append(line[0]).append("\t\t");
            stringBuilder.append("Purse number: ").append(line[2]).append("\n");
        }
        send(stringBuilder.toString());
    }

    private void getInfo() {
        String str = "Login: " + user.getLogin() + "\n" +
                "Purse number: " + user.getId() + "\n" +
                "Purse size: " + user.getSize();
        send(str);
    }

    private void signin(String login, String password) throws IOException {
        user = getUserInfo(login);
        if (user == null) {
            send(ERR_SIGN);
            return;
        }
        if (!user.getPassword().equals(password)) {
            send(ERR_SIGN);
            return;
        }
        send("u r in!");
    }

    private void signup(String login, String password) throws IOException {
        user = new User(login, password);
        for (String str : Files.readAllLines(Paths.get(PATH_TO_CLIENTS_INFO))) {
            String[] line = str.split("\\s+");
            if (line[0].equals(login)) {
                send("This login is already used");
                return;
            }
        }
        Utils.addLineInFile(user.getInfo() + "\n", PATH_TO_CLIENTS_INFO);
        send("u r in!");
    }

    private void downService() {
        try {
            if (!socket.isClosed()) {
                socket.close();
                in.close();
                out.close();
                for (ServerSession vr : server.getClientList()) {
                    if (vr.equals(this)) vr.interrupt();
                    server.removeClient(this);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private User getUserInfo(String login) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(PATH_TO_CLIENTS_INFO));
        for (String l : lines) {
            String[] strings = l.split("\\s+");
            if (strings[0].equals(login)) {
                return new User(strings);
            }
        }
        return null;
    }

    private void updateUserSizeInfo(User user) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(PATH_TO_CLIENTS_INFO));
        for (int i = 0; i < lines.size(); i++) {
            String[] strings = lines.get(i).split("\\s+");
            if (strings[0].equals(user.getLogin())) {
                strings[3] = Integer.toString(user.getSize());
                StringBuilder stringBuilder = new StringBuilder();
                for (String str : strings) stringBuilder.append(str).append(" ");
                lines.set(i, stringBuilder.toString());
            }
        }
        Files.write(Paths.get(PATH_TO_CLIENTS_INFO), lines);
    }

    private void disconnect() throws IOException {
        socket.close();
        server.removeClient(this);
        connect = false;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
