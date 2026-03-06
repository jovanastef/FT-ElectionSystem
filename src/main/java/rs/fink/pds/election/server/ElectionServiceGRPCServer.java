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
        // za dodavanje rezultata glasanja
        AccountResponse response = electionAppServer.addVotingResult(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
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
