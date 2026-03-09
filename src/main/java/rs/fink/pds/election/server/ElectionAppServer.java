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
        System.out.println("[EXEC] Executing command: " + commandStr);
        
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
    	System.out.println("[EXEC] executeAddVotingResult: " + String.join("|", parts));
    	// ADD_VOTING_RESULT|stationId|controllerId|totalVoters|invalidBallots|electionType
        try {
            int stationId = Integer.parseInt(parts[1]);
            String controllerId = parts[2];
            int totalVoters = Integer.parseInt(parts[3]);
            int invalidBallots = Integer.parseInt(parts[4]);
            String electionType = parts[5];
            
            System.out.println("[EXEC] Calling electionService.addVotingResult...");
            electionService.addVotingResult(stationId, controllerId, totalVoters, 
                                           invalidBallots, new java.util.HashMap<>(), 
                                           new java.util.HashMap<>());
            
            System.out.println("[EXEC] Command executed successfully!");
        } catch (Exception e) {
            System.err.println("Error executing AddVotingResult: " + e.getMessage());
            e.printStackTrace();
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
    
    //helper metod za dodavanje rezultata sa individualnim parametrima
    //koristi se od strane grpc servera
    public boolean addVotingResult(int pollingStationId, String controllerId, 
            int totalVoters, int invalidBallots) {
    	if (myReplicaNode == null || myReplicaNode.getReplicatedLog() == null) {
            System.err.println("SERVER NOT READY: ReplicaNode or Log is null!");
            return false;
        }
    	
    	System.out.println("DEBUG: addVotingResult called - station=" + pollingStationId);
    	
    	// Proveri da li smo lider
		if (!myReplicaNode.isLeader()) {
			System.err.println("Not leader, rejecting request for station #" + pollingStationId);
			return false;
		}
		
		try {
			System.out.println("DEBUG: Creating AddVotingResultCommand");
			// Kreiraj komandu sa svim potrebnim podacima
			AddVotingResultCommand command = new AddVotingResultCommand(
				pollingStationId,
				controllerId,
				totalVoters,
				invalidBallots,
				"parlamentarni"  // electionType
			);
			
			System.out.println("DEBUG: Calling appendAndReplicate");
			
			// Upisi u log i repliciraj
			myReplicaNode.getReplicatedLog().appendAndReplicate(command.serialize());
			
			System.out.println("DEBUG: Executing command directly on leader");
	        electionService.addVotingResult(pollingStationId, controllerId, totalVoters,
	                                       invalidBallots, new java.util.HashMap<>(),
	                                       new java.util.HashMap<>());
	        
	        System.out.println("Voting result logged for station #" + pollingStationId);
	        return true;
			
		} catch (IOException e) {
	        System.err.println("Log error: " + e.getMessage());
	        e.printStackTrace(); 
	        return false;
	    } catch (Exception e) {
	        System.err.println("Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
	        e.printStackTrace();  
	        return false;
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
    	myReplicaNode.leaderElection();
        myReplicaNode.start();
        
        Thread.sleep(1000);
        
        gRPCServer = ServerBuilder
            .forPort(Integer.parseInt(myReplicaNode.getMyGRPCAddress().split(":")[1]))
            .addService(new ElectionServiceGRPCServer(this))
            .addService(myReplicaNode)
            .build();
        
        gRPCServer.start();
        
        
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
            String myGRPCAddress = "127.0.0.1:" + gRPCPort;
            
            ElectionAppServer server = new ElectionAppServer(
                zkConnectionString, APP_ROOT_NODE, myGRPCAddress, logFileName, snapshotDir);
           
            
            // shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down election server...");
                server.stop();
            }));
            
            server.start();
            
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    System.out.println("Testing addVotingResult directly...");
                    boolean result = server.addVotingResult(999, "TEST_CTRL", 100, 0);
                    System.out.println("Direct test result: " + result);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            
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
    