package rs.fink.pds.election.client;

import java.util.List;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;

import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import rs.fink.pds.faulttolerance.core.SyncPrimitive;
import rs.fink.pds.faulttolerance.gRPC.*;
import rs.fink.pds.election.server.ElectionAppServer;

public class ElectionClient extends SyncPrimitive {
    private final String appRoot;
    private String leaderNodeName = null;
    private String leaderHostNamePort;
    private final Object zkNotifier = new Object();
    private ManagedChannel channel = null;
    private AccountServiceGrpc.AccountServiceBlockingStub blockingStub = null;
    
    public ElectionClient(String zkAddress, String appRoot) throws KeeperException, InterruptedException {
        super(zkAddress);
        this.appRoot = appRoot;
    }
    
    @Override
    public void process(WatchedEvent event) {
        System.out.println("ZooKeeper notification received!");
        try {
            synchronized(zkNotifier) {
                zkNotifier.notify();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private void newLeaderAwaiting() throws KeeperException, InterruptedException {
        System.out.println("Awaiting ZooKeeper notification or timeout");
        synchronized(zkNotifier) {
            zkNotifier.wait(5000);
        }
        checkLeader();
    }
    
    public synchronized void checkLeader() throws KeeperException, InterruptedException {
        List<String> list = zk.getChildren(appRoot, true); //true prosledjujem da bi se aktivirao watcher
        
        if (list.isEmpty()) {
            System.out.println("No replicas available! Awaiting for servers...");
            newLeaderAwaiting();
            return;
        }
        
        // Find leader (lowest sequence number = first alphabetically)
        String minNodeName = list.get(0);
        int minValue = Integer.parseInt(minNodeName.substring(minNodeName.length() - 10));
        
        for(int i = 1; i < list.size(); i++) {
            String nodeName = list.get(i);
            int value = Integer.parseInt(nodeName.substring(nodeName.length() - 10));
            if(value < minValue) {
                minValue = value;
                minNodeName = nodeName;
            }
        }
        
        if (leaderNodeName == null || !minNodeName.equals(leaderNodeName)) {
            leaderNodeName = minNodeName;
            byte[] b = zk.getData(appRoot + "/" + leaderNodeName, true, null);
            if (b != null) {
                leaderHostNamePort = new String(b, java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            System.out.println("Leader is: " + leaderNodeName + " at " + leaderHostNamePort);
            blockingStub = getBlockingStub(leaderHostNamePort);
        }
    }
    
    private AccountServiceGrpc.AccountServiceBlockingStub getBlockingStub(String hostNamePort) {
        // Ocisti string od SVIH nevidljivih karaktera, razmaka i smeca
        // Zadrzavamo samo brojeve, tacku i dvotacku
        String cleanAddress = hostNamePort.replaceAll("[^0-9.:]", "");
        
        System.out.println("DEBUG: Original address: [" + hostNamePort + "]");
        System.out.println("DEBUG: Cleaned address: [" + cleanAddress + "]");

        String[] splits = cleanAddress.split(":");
        if (splits.length < 2) {
            throw new IllegalArgumentException("Invalid host:port format after cleaning: " + cleanAddress);
        }

        String host = splits[0].trim();
        int port = Integer.parseInt(splits[1].trim());

        System.out.println("DEBUG: Connecting to gRPC host: [" + host + "] port: [" + port + "]");

        // Zatvori stari kanal ako postoji
        if (channel != null) {
            channel.shutdownNow();
        }

        channel = OkHttpChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        
        return AccountServiceGrpc.newBlockingStub(channel);
    }
    
    /**
     * Simulira unos rezultata od kontrolora na birackom mestu
     */
    public void submitVotingResult(int stationId, int totalVoters, int invalidBallots) {
        try {
            checkLeader();
            
            AccountRequest request = AccountRequest.newBuilder()
                .setRequestId(stationId)
                .setOpType(AccountRequestType.ADD)
                .setAmount(totalVoters)
                .build();
            
            AccountResponse response = blockingStub.addAmount(request);
            
            if (response.getStatus() == RequestStatus.STATUS_OK) {
                System.out.println("Voting result submitted for station #" + stationId);
                System.out.println("   Message: " + response.getMessage());
            } else if (response.getStatus() == RequestStatus.UPDATE_REJECTED_NOT_LEADER) {
                System.out.println("Rejected - not leader, finding new leader...");
                newLeaderAwaiting();
                submitVotingResult(stationId, totalVoters, invalidBallots);
            } else {
                System.out.println("Error: " + response.getStatus());
            }
        } catch (Exception e) {
            System.err.println("Error submitting result: " + e.getMessage());
            e.printStackTrace();
            try {
                newLeaderAwaiting();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Preuzima statistiku izbora
     */
    public void getStatistics() {
        try {
            checkLeader();
            
            AccountRequest request = AccountRequest.newBuilder()
                .setRequestId(1)
                .setOpType(AccountRequestType.GET)
                .build();
            
            AccountResponse response = blockingStub.getAmount(request);
            
            if (response.getStatus() == RequestStatus.STATUS_OK) {
                System.out.println("Statistics: " + response.getMessage());
                System.out.println("Turnout: " + response.getBalance() + "%");
            } else {
                System.out.println("Error getting statistics: " + response.getStatus());
            }
        } catch (Exception e) {
            System.err.println("Error getting statistics: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
            System.out.println("Client channel shutdown");
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -cp ... ElectionClient <zookeeper_host:port>");
            System.exit(1);
        }
        
        try {
            ElectionClient client = new ElectionClient(args[0], ElectionAppServer.APP_ROOT_NODE);
            client.checkLeader();
            
            System.out.println("\nSimulating voting result submissions from controllers...\n");
            
            // Simuliraj unos rezultata od vise kontrolora za isto mesto
            client.submitVotingResult(1, 850, 12);   // Kontrolor 1
            client.submitVotingResult(1, 850, 12);   // Kontrolor 2 (isti rezultat = validno)
            
            // Drugo biracko mesto
            client.submitVotingResult(2, 920, 8);
            client.submitVotingResult(2, 920, 8);
            
            System.out.println("\nFetching election statistics...\n");
            client.getStatistics();
            
            client.shutdown();
            
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}