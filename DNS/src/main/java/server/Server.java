package server;


import dnsPackage.enams.RCode;
import dnsPackage.enams.RRType;
import dnsPackage.parts.Answer;
import dnsPackage.parts.Flags;
import dnsPackage.parts.Header;
import dnsPackage.parts.Query;
import dnsPackage.utilits.PackageBuilder;
import dnsPackage.utilits.PackageReader;

import javax.security.auth.callback.PasswordCallback;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Server {
    static final int HEADER_LEN = 12;
    static final int DEF_PORT = 53;
    static final String ROOT_SERVER = "199.7.83.42";//"192.36.148.17";
    private DatagramSocket socket;

    public Server() {
        try {
            socket = new DatagramSocket(4444);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void run() throws UnknownHostException {
        while (true) {
            DatagramPacket packet = receive();
            InetAddress clientArddess = packet.getAddress();
            int clientPort = packet.getPort();
            PackageReader packageReader = new PackageReader();
            packageReader.read(packet.getData());

            List<Answer> answers = findAnswers(packageReader.getQueries());
            if (answers.size() != 0) {
                PackageBuilder packageBuilder = new PackageBuilder()
                        .addHeader(packageReader.getHeader())
                        .addQuery(packageReader.getQueries())
                        .addAnswer(answers)
                        .build();
                send(clientArddess, clientPort, packageBuilder.getBytes());
                continue;
            }

            byte[] clientQuery = new PackageBuilder()
                    .addHeader(packageReader.getHeader())
                    .addQuery(packageReader.getQueries())
                    .build()
                    .getBytes();

            String tempAddress = ROOT_SERVER;
            String tempName = "Root server";

            Stack<String> addressServerStack = new Stack<>();
            addressServerStack.push(tempAddress);
            Stack<String> nameServerStack = new Stack<>();
            nameServerStack.push(tempName);
            while (true) {
                tempAddress = addressServerStack.pop();
                tempName = nameServerStack.pop();
                System.out.println("Sending to \"" + tempName + "\", address: \"" + tempAddress + "\".\n");
                send(InetAddress.getByName(tempAddress), DEF_PORT, clientQuery);
                PackageBuilder packageBuilder = new PackageBuilder();
                packageReader = new PackageReader();
                packageReader.read(receive().getData());
                System.out.println("Received from \"" + tempName + "\"\n" + packageReader.toString());

                if (packageReader.getHeader().getFlags().getRCode() != RCode.NO_ERR) {
                    packageBuilder.addHeader(packageReader.getHeader())
                            .addQuery(packageReader.getQueries())
                            .build();
                    send(clientArddess, clientPort, packageBuilder.getBytes());
                    break;
                }

                if (packageReader.getHeader().getAnCount() > 0) {
                    send(clientArddess, clientPort, buildAnswer(packageReader));
                    break;
                }

                for (Answer authoritative : packageReader.getAuthoritative()) {
                    tempName = authoritative.getrDataString();
                    for (Answer additional : packageReader.getAdditional()) {
                        if (additional.getRrType() == RRType.A && additional.getNameString().equals(tempName)) {
                            nameServerStack.push(additional.getNameString());
                            addressServerStack.push(additional.getATypeData());
                        }
                    }
                }
            }
        }
    }

    private byte[] buildAnswer(PackageReader packageReader) {
        PackageBuilder packageBuilder = new PackageBuilder()
                .addHeader(packageReader.getHeader())
                .addQuery(packageReader.getQueries());
        for (Query query : packageReader.getQueries()) {
            for (Answer answer : packageReader.getAnswers()) {
                if (query.getDomainName().equals(answer.getNameString())) packageBuilder.addAnswer(answer);
            }
        }
        packageBuilder.build();
        return packageBuilder.getBytes();
    }

    private void send(InetAddress address, int port, byte[] bytes) {
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);
        try {
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DatagramPacket receive() {
        byte[] bytes = new byte[512];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        try {
            socket.receive(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return packet;
    }

    public void close() {
        socket.close();
    }


    private File getFilePathResources(String fileName) {

        ClassLoader classLoader = getClass().getClassLoader();

        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file is not found!");
        } else {
            return new File(resource.getFile());
        }

    }

    public List<Answer> findAnswers(List<Query> queryList) {
        File file = getFilePathResources("ResourceRecords");
        List<Answer> answers = new ArrayList<>();
        int shift = HEADER_LEN;
        for (Query query : queryList) {
            try {
                try (FileReader reader = new FileReader(file);
                     BufferedReader br = new BufferedReader(reader)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split("\\s+");
                        if (parts[0].equals(query.getDomainName()) &&
                                parts[2].equals(query.getRrClass().toString()) &&
                                parts[3].equals(query.getRrType().toString())) {
                            answers.add(new Answer()
                                    .setName(shift)
                                    .setTtl(Integer.parseInt(parts[1]))
                                    .setRRClass(query.getRrClass())
                                    .setRRType(query.getRrType())
                                    .makeAnswer(parts[4]));
                        }
                    }
                    shift += query.length();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return answers;
    }

    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.run();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}