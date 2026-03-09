package rs.fink.pds.election.server;

import java.io.IOException;

import io.grpc.stub.StreamObserver;
import rs.fink.pds.faulttolerance.gRPC.*;
import rs.fink.pds.election.commands.CreateSnapshotCommand;
import rs.fink.pds.election.server.ElectionServiceImpl.ElectionStatistics;

public class ElectionServiceGRPCServer extends AccountServiceGrpc.AccountServiceImplBase {
    private final ElectionAppServer electionAppServer;
    
    public ElectionServiceGRPCServer(ElectionAppServer server) {
        this.electionAppServer = server;
    }
    
    @Override
    public void addAmount(AccountRequest request, StreamObserver<AccountResponse> responseObserver) {
    	System.out.println("[GRPC] addAmount CALLED - requestId=" + request.getRequestId());
    	// za dodavanje rezultata glasanja
    	try {
    		System.out.println("DEBUG: addAmount called with requestId=" + request.getRequestId());
    		int pollingStationId = request.getRequestId();
            int totalVoters = (int) request.getAmount();
            int invalidBallots = request.hasInvalidBallots() 
                    ? request.getInvalidBallots() 
                    : 0; //citaj iz requesta
            String controllerId = "CTRL_" + pollingStationId + "_" + 
                    System.currentTimeMillis() + "_" + 
                    (int)(Math.random() * 1000);
            
            System.out.println("DEBUG: Generated controllerId=" + controllerId);
            System.out.println("DEBUG: Calling electionAppServer.addVotingResult()");
            
            boolean success = electionAppServer.addVotingResult(
                pollingStationId, 
                controllerId, 
                totalVoters, 
                invalidBallots
            );
            
            // Dodaj snapshot nakon svake uspesne operacije (za testiranje)
            if (success && request.getRequestId() % 5 == 0) {
                try {
                    long logIndex = electionAppServer.getReplicaNode()
                        .getReplicatedLog().getLastLogEntryIndex();
                    CreateSnapshotCommand snapshotCmd = new CreateSnapshotCommand(logIndex);
                    electionAppServer.getReplicaNode().getReplicatedLog()
                        .appendAndReplicate(snapshotCmd.serialize());
                    System.out.println("Snapshot triggered at index: " + logIndex);
                } catch (IOException e) {
                    System.err.println("Failed to create snapshot: " + e.getMessage());
                }
            }
            
            System.out.println("DEBUG: addVotingResult returned: " + success);
            
            AccountResponse response;
            if (success) {
                response = AccountResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setStatus(RequestStatus.STATUS_OK)
                    .setMessage("Voting result added for station #" + pollingStationId)
                    .build();
            } else {
                response = AccountResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setStatus(RequestStatus.UPDATE_REJECTED_NOT_LEADER)
                    .setMessage("Result rejected - needs majority consensus")
                    .build();
    	}
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        
    	} catch (Exception e) {
            // Vrati jasniju gresku umesto UNKNOWN
    		System.err.println("EXCEPTION in addAmount:");
            e.printStackTrace();
            
            AccountResponse response = AccountResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setStatus(RequestStatus.UNRECOGNIZED)
                .setMessage("Server error: " + e.getMessage())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void withdrawAmount(AccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        // Ne koristi se za izbore
        AccountResponse response = AccountResponse.newBuilder()
            .setRequestId(request.getRequestId())
            .setStatus(RequestStatus.UNRECOGNIZED)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    @Override
    public void getAmount(AccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        // za dobijanje statistike
        AccountResponse response = electionAppServer.getStatistics(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
