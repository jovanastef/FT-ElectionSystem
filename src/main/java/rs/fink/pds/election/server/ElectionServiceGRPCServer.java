package rs.fink.pds.election.server;

import io.grpc.stub.StreamObserver;
import rs.fink.pds.faulttolerance.gRPC.*;
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
            int invalidBallots = 0; // Default, moze se dodati u request
            String controllerId = "CTRL_" + request.getRequestId(); // Placeholder
            
            System.out.println("DEBUG: Calling electionAppServer.addVotingResult()");
            
            boolean success = electionAppServer.addVotingResult(
                pollingStationId, 
                controllerId, 
                totalVoters, 
                invalidBallots
            );
            
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
