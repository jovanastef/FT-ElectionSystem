package rs.fink.pds.election.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import org.apache.zookeeper.KeeperException;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import rs.fink.pds.faulttolerance.core.ReplicaNode;
import rs.fink.pds.faulttolerance.core.SnapshotManager;
import rs.fink.pds.faulttolerance.gRPC.AccountRequest;
import rs.fink.pds.faulttolerance.gRPC.AccountResponse;
import rs.fink.pds.faulttolerance.gRPC.RequestStatus;
import rs.fink.pds.election.commands.*;
import rs.fink.pds.election.commands.Command.CommandType;
import rs.fink.pds.election.model.ElectionState;

public class ElectionAppServer implements ReplicaNode.LogCommandExecutor {
    public static final String APP_ROOT_NODE = "/election";
    
    private final ReplicaNode myReplicaNode;
    private final ElectionServiceImpl electionService;
    private final SnapshotManager snapshotManager;
    private Server gRPCServer;
    
    public ElectionAppServer(String zkAddress, String zkRoot, String myGRPCAddress, 
                            String logFileName, String snapshotDir) throws FileNotFoundException {
        this.electionService = new ElectionServiceImpl();
        this.snapshotManager = new SnapshotManager(snapshotDir);
        this.myReplicaNode = new ReplicaNode(zkAddress, zkRoot, myGRPCAddress, logFileName, this);
        
        // Ucitaj stanje iz snapshota ako postoji
        loadStateFromSnapshot();
    }
    
    private void loadStateFromSnapshot() {
        ElectionState state = snapshotManager.loadLatestSnapshot();
        if (state.getLastSnapshotIndex() > 0) {
            electionService.loadFromSnapshot(state);
            System.out.println("Loaded state from snapshot at index: " + state.getLastSnapshotIndex());
        }
    }
    
    @Override
    public void executeReplicatedLogCommand(byte[] commandBytes) {
        String commandStr = new String(commandBytes);
        String[] parts = commandStr.split("\\|");
        
        if (parts.length < 1) return;
        
        CommandType type = CommandType.valueOf(parts[0]);
        
        switch (type) {
            case ADD_VOTING_RESULT:
                executeAddVotingResult(parts);
                break;
            case VERIFY_RESULTS:
                executeVerifyResults(parts);
                break;
            case CREATE_SNAPSHOT:
                executeCreateSnapshot(parts);
                break;
            default:
                System.err.println("Unknown command type: " + type);
        }
    }
    
    private void executeAddVotingResult(String[] parts) {
        // ADD_VOTING_RESULT|stationId|controllerId|totalVoters|invalidBallots|electionType
        try {
            int stationId = Integer.parseInt(parts[1]);
            String controllerId = parts[2];
            int totalVoters = Integer.parseInt(parts[3]);
            int invalidBallots = Integer.parseInt(parts[4]);
            String electionType = parts[5];
            
            electionService.addVotingResult(stationId, controllerId, totalVoters, 
                                           invalidBallots, new java.util.HashMap<>(), 
                                           new java.util.HashMap<>());
        } catch (Exception e) {
            System.err.println("Error executing AddVotingResult: " + e.getMessage());
        }
    }
    
    private void executeVerifyResults(String[] parts) {
        // VERIFY_RESULTS|stationId
        try {
            int stationId = Integer.parseInt(parts[1]);
            electionService.getStatistics("all");
        } catch (Exception e) {
            System.err.println("Error executing VerifyResults: " + e.getMessage());
        }
    }
    
    private void executeCreateSnapshot(String[] parts) {
        // CREATE_SNAPSHOT|logIndex
        try {
            long logIndex = Long.parseLong(parts[1]);
            snapshotManager.createSnapshot(electionService.getElectionState(), logIndex);
        } catch (Exception e) {
            System.err.println("Error creating snapshot: " + e.getMessage());
        }
    }
    
    public AccountResponse addVotingResult(AccountRequest request) {
        if (!myReplicaNode.isLeader()) {
            return AccountResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setStatus(RequestStatus.UPDATE_REJECTED_NOT_LEADER)
                .setMessage("Not leader")
                .build();
        }
        
        // Kreiraj komandu i upisi u log
        AddVotingResultCommand command = new AddVotingResultCommand(
            request.getRequestId(),
            "controller_" + request.getRequestId(),
            (int)request.getAmount(),
            0,
            "parlamentarni"
        );
        
        try {
            myReplicaNode.getReplicatedLog().appendAndReplicate(command.serialize());
            
            // Proveri da li treba kreirati snapshot
            if (snapshotManager.shouldCreateSnapshot(
                    myReplicaNode.getReplicatedLog().getLastLogEntryIndex())) {
                CreateSnapshotCommand snapshotCmd = new CreateSnapshotCommand(
                    myReplicaNode.getReplicatedLog().getLastLogEntryIndex());
                myReplicaNode.getReplicatedLog().appendAndReplicate(snapshotCmd.serialize());
            }
            
            return AccountResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setStatus(RequestStatus.STATUS_OK)
                .setMessage("Voting result added")
                .build();
        } catch (IOException e) {
            return AccountResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setStatus(RequestStatus.UNRECOGNIZED)
                .setMessage("Log error: " + e.getMessage())
                .build();
        }
    }
    
    public AccountResponse getStatistics(AccountRequest request) {
        ElectionServiceImpl.ElectionStatistics stats = electionService.getStatistics("all");
        
        String message = String.format("Stations: %d/%d, Turnout: %.2f%%",
            stats.stationsWithResults, stats.totalStations, stats.turnoutPercentage);
        
        return AccountResponse.newBuilder()
            .setRequestId(request.getRequestId())
            .setStatus(RequestStatus.STATUS_OK)
            .setBalance((float)stats.turnoutPercentage)
            .setMessage(message)
            .build();
    }
    
    public void start() throws IOException, KeeperException, InterruptedException {
        gRPCServer = ServerBuilder
            .forPort(Integer.parseInt(myReplicaNode.getMyGRPCAddress().split(":")[1]))
            .addService(new ElectionServiceGRPCServer(this))
            .addService(myReplicaNode)
            .build();
        
        gRPCServer.start();
        myReplicaNode.leaderElection();
        myReplicaNode.start();
        
        System.out.println("Election server started on: " + myReplicaNode.getMyGRPCAddress());
        gRPCServer.awaitTermination();
    }
    
    public void stop() {
        if (gRPCServer != null) {
            gRPCServer.shutdown();
        }
        myReplicaNode.stop();
    }
    
    public ReplicaNode getReplicaNode() {
        return myReplicaNode;
    }
    
    public ElectionServiceImpl getElectionService() {
        return electionService;
    }
    
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java -cp ... ElectionAppServer <zookeeper_host:port> <grpc_port> <log_file> <snapshot_dir>");
            System.exit(1);
        }
        
        try {
            String zkConnectionString = args[0];
            int gRPCPort = Integer.parseInt(args[1]);
            String logFileName = args[2];
            String snapshotDir = args[3];
            String myGRPCAddress = InetAddress.getLocalHost().getHostName() + ":" + gRPCPort;
            
            ElectionAppServer server = new ElectionAppServer(
                zkConnectionString, APP_ROOT_NODE, myGRPCAddress, logFileName, snapshotDir);
           
            
            // shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down election server...");
                server.stop();
            }));
            
            server.start();
            
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (KeeperException e) {
            System.err.println("ZooKeeper Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("Interrupted: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
}
    