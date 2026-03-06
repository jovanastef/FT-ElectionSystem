package rs.fink.pds.election.model;

public class Controller {
    private final String controllerId;
    private final String name;
    private final int pollingStationId;
    private boolean hasSubmittedResult = false;
    private VotingResult submittedResult = null;
    
    public Controller(String controllerId, String name, int pollingStationId) {
        this.controllerId = controllerId;
        this.name = name;
        this.pollingStationId = pollingStationId;
    }
    
    public String getControllerId() { return controllerId; }
    public String getName() { return name; }
    public int getPollingStationId() { return pollingStationId; }
    public boolean hasSubmittedResult() { return hasSubmittedResult; }
    public VotingResult getSubmittedResult() { return submittedResult; }
    
    public void submitResult(VotingResult result) {
        this.hasSubmittedResult = true;
        this.submittedResult = result;
    }
    
    public void resetSubmission() {
        this.hasSubmittedResult = false;
        this.submittedResult = null;
    }
}