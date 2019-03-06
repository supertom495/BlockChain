import java.util.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class PeriodicLatestBlockRunnable implements Runnable {

    private ArrayList<ServerInfo> serverInfos;
    private Blockchain blockchain;
    private int localPort;


    public PeriodicLatestBlockRunnable(HashMap<ServerInfo, Date> serverStatus, Blockchain blockchain, int localPort) {
        this.serverInfos = new ArrayList<>(serverStatus.keySet());
        this.blockchain = blockchain;
        this.localPort = localPort;
    }

    @Override
    public void run() {
        while (true) {
            // mulity-cast current block hash to choosen peers
            ArrayList<Thread> threadArrayList = new ArrayList<>();
            ArrayList<ServerInfo> peers = getFiveRandomPeers(serverInfos);
            if (this.blockchain.getHead() != null) {

                for (ServerInfo si : peers) {
                    Thread thread = new Thread(new HeartBeatClientRunnable(si, "lb|" + localPort + "|" +
                            this.blockchain.getLength() + "|" + Base64.getEncoder().encodeToString(this.blockchain.getHead().calculateHash())));
                    threadArrayList.add(thread);
                    thread.start();

                }

                for (Thread thread : threadArrayList) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                }
            }


            // sleep for two seconds
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }

    private ArrayList<ServerInfo> getFiveRandomPeers(ArrayList<ServerInfo> serverInfos) {

        if (serverInfos.size() <= 5) return new ArrayList<>(serverInfos);

        Collections.shuffle(serverInfos);

        return new ArrayList<>(serverInfos.subList(0, 5));

    }

}