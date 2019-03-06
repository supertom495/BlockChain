import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.*;

public class BlockchainServerRunnable implements Runnable {

    private Socket clientSocket;
    private Blockchain blockchain;
    private HashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, HashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public void serverHandler(InputStream clientInputStream, OutputStream clientOutputStream) {


        BufferedReader inputReader = new BufferedReader(
                new InputStreamReader(clientInputStream));
        PrintWriter outWriter = new PrintWriter(clientOutputStream, true);

        try {
            while (true) {
                String inputLine = inputReader.readLine();
                if (inputLine == null) {
                    break;
                }

                String[] tokens = inputLine.split("\\|");

                String localIP = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
                String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
                int localPort = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getPort());
                int remotePort;


                ArrayList<Thread> threadArrayList;

                switch (tokens[0]) {

                    case "lb":
                        remotePort = Integer.parseInt(tokens[1]);
                        int remoteBlockchainLength = Integer.parseInt(tokens[2]);
                        String remoteBlockchainHash = tokens[3];

                        ServerInfo remoteSI = new ServerInfo(remoteIP, remotePort);

                        if (blockchain.getLength() < remoteBlockchainLength) {
                            blockchain = getRemoteBlockchain(blockchain, remoteSI, remoteBlockchainHash);

                        } else if (blockchain.getLength() == remoteBlockchainLength) {

                            byte[] remoteHash = Base64.getDecoder().decode(remoteBlockchainHash);
                            byte[] currentHash = blockchain.getHead().calculateHash();

                            boolean isSmaller = true;

                            if(Arrays.equals(remoteHash, currentHash)) isSmaller = false;
                            for(int i = 0; i < remoteHash.length; i++)
                                if(currentHash[i] > remoteHash[i]) isSmaller = false;

                            if (isSmaller) {
                                blockchain = getRemoteBlockchain(blockchain, remoteSI, remoteBlockchainHash);
                            }
                        }

                        break;
                    case "cu":
                        ObjectOutputStream oos = new ObjectOutputStream(clientOutputStream);
                        if (tokens.length == 1) {
                            oos.writeObject(blockchain.getHead());
                        } else {
                            oos.writeObject(blockchain.getBlockWithHash(Base64.getDecoder().decode(tokens[1])));
                        }
                        oos.flush();
                        break;
                    case "si":
                        remotePort = Integer.parseInt(tokens[1]);
                        ServerInfo senderSI = new ServerInfo(remoteIP, remotePort);
                        ServerInfo localSI = new ServerInfo(localIP, localPort);
                        remoteSI = new ServerInfo(tokens[2], Integer.parseInt(tokens[3]));

                        if (serverStatus.get(remoteSI) != null) break; // the server knows this info before

                        serverStatus.put(remoteSI, new Date());

                        // relay
                        threadArrayList = new ArrayList<>();
                        for (ServerInfo si : serverStatus.keySet()) {
                            if (si.equals(remoteSI) ||
                                    si.equals(localSI) ||
                                    si.equals(senderSI)) {
                                continue;
                            }
                            Thread thread = new Thread(new HeartBeatClientRunnable(si, "si|" + localPort + "|" + tokens[2] + "|" + Integer.parseInt(tokens[3])));
                            threadArrayList.add(thread);
                            thread.start();
                        }
                        for (Thread thread : threadArrayList) {
                            thread.join();
                        }

                        break;

                    case "hb":
                        remotePort = Integer.parseInt(tokens[1]);
                        serverStatus.put(new ServerInfo(remoteIP, remotePort), new Date());

                        if (tokens[2].equals("0")) {
                            threadArrayList = new ArrayList<>();
                            //broadcast
                            for (ServerInfo si : serverStatus.keySet()) {
                                if ((si.getHost().equals(localIP) && si.getPort() == localPort) ||
                                        (si.getHost().equals(remoteIP) && si.getPort() == remotePort)) {
                                    continue;
                                }

                                Thread thread = new Thread(new HeartBeatClientRunnable(si, "si|" + localPort + "|" + remoteIP + "|" + remotePort));

                                threadArrayList.add(thread);
                                thread.start();
                            }
                            for (Thread thread : threadArrayList) {
                                thread.join();
                            }
                        }
                        break;

                    case "tx":
                        if (blockchain.addTransaction(inputLine))
                            outWriter.print("Accepted\n\n");
                        else
                            outWriter.print("Rejected\n\n");
                        outWriter.flush();
                        break;
                    case "pb":
                        outWriter.print(blockchain.toString() + "\n");
                        outWriter.flush();
                        break;
                    case "cc":
                        return;
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }
            }
        } catch (IOException e) {
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized static Blockchain getRemoteBlockchain(Blockchain blockchain, ServerInfo remoteSI, String remoteBlockchainHash) {


        Stack<Block> blockStack = new Stack();

        String message = "cu|" + remoteBlockchainHash;

        Block newBlock = BlockchainServer.getNewBlock(remoteSI, message);

        blockStack.push(newBlock);

        while (!blockchain.containsHash(newBlock.getPreviousHash())) {
            if (Arrays.equals(newBlock.getPreviousHash(), new byte[32])) break;
            message = "cu|" + Base64.getEncoder().encodeToString(newBlock.getPreviousHash());
            newBlock = BlockchainServer.getNewBlock(remoteSI, message);
            blockStack.push(newBlock);

        }

        byte[] hash = blockStack.peek().getPreviousHash();

        Block currentBlock = blockchain.getBlockWithHash(hash);

        while (!blockStack.isEmpty()) {
            newBlock = blockStack.pop();
            newBlock.setPreviousBlock(currentBlock);
            blockchain.setHead(newBlock);
            blockchain.setLength(blockchain.getLength() + 1);
            currentBlock = newBlock;
        }


        return blockchain;

    }

}