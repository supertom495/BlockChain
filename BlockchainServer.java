import java.io.*;
import java.net.*;
import java.util.*;

public class BlockchainServer {

    public static void main(String[] args) {

        if (args.length != 3) {
            return;
        }

        int localPort = 0;
        int remotePort = 0;
        String remoteHost = null;

        try {
            localPort = Integer.parseInt(args[0]);
            remoteHost = args[1];
            remotePort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return;
        }

        HashMap<ServerInfo, Date> serverStatus = new HashMap<ServerInfo, Date>();

        ServerInfo remoteSI = new ServerInfo(remoteHost, remotePort);

        serverStatus.put(remoteSI, new Date());

        Blockchain blockchain = getRemoteServerBlockChain(remoteSI);


        PeriodicCommitRunnable pcr = new PeriodicCommitRunnable(blockchain);
        Thread pct = new Thread(pcr);
        pct.start();

        // start up PeriodicPrinter
        new Thread(new PeriodicPrinterRunnable(serverStatus)).start();

        // start up PeriodicHeartBeat
        new Thread(new PeriodicHeartBeatRunnable(serverStatus, localPort)).start();

        // start up PeriodicLatestBlock
        new Thread(new PeriodicLatestBlockRunnable(serverStatus, blockchain, localPort)).start();


        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(localPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new BlockchainServerRunnable(clientSocket, blockchain, serverStatus)).start();
            }
        } catch (IllegalArgumentException e) {
        } catch (IOException e) {
        } finally {
            try {
                pcr.setRunning(false);
                pct.join();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
            } catch (InterruptedException e) {
            }
        }
    }


    public static Blockchain getRemoteServerBlockChain(ServerInfo remoteServer) {

        Blockchain blockchain = new Blockchain();

        // send the message forward
        String message = "cu";

        Block newBlock = getNewBlock(remoteServer, message);

        // return if the blockchian in the remote server is empty
        if (newBlock == null) return blockchain;

        blockchain.setHead(newBlock);

        int length = 1;
        Block currentBlock = newBlock;

        // an empty byte to test if there is no previous block
        byte newByte[] = new byte[32];

        while (!Arrays.equals(newBlock.getPreviousHash(), newByte)) {
            message = "cu|" + Base64.getEncoder().encodeToString(newBlock.getPreviousHash());
            newBlock = getNewBlock(remoteServer, message);
            currentBlock.setPreviousBlock(newBlock);
            currentBlock = newBlock;
            length++;
        }

        blockchain.setLength(length);


        return blockchain;
    }

    public static Block getNewBlock(ServerInfo si, String message) {
        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(si.getHost(), si.getPort()), 2000);
            PrintWriter pw = new PrintWriter(newSocket.getOutputStream(), true);

            pw.println(message);
            pw.flush();

            ObjectInputStream ois = new ObjectInputStream(newSocket.getInputStream());

            Block newBlock = (Block) ois.readObject();


            newSocket.close();
            return newBlock;

        } catch (IOException | ClassNotFoundException e) {
        }
        // impossible
        return null;
    }

}
