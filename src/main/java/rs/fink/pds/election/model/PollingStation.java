package rs.fink.pds.election.model;

import java.util.ArrayList;
import java.util.List;
import rs.fink.pds.election.model.Controller;

public class PollingStation {
    private final int stationId;
    private final String electionType; // lokalni, parlamentarni, predsednicki
    private final int registeredVoters;
    private final List<Controller> controllers;
    private VotingResult votingResult;
    private boolean needsReentry = false;
    
    public PollingStation(int stationId, String electionType, int registeredVoters) {
        this.stationId = stationId;
        this.electionType = electionType;
        this.registeredVoters = registeredVoters;
        this.controllers = new ArrayList<>();
    }
    
    public int getStationId() { return stationId; }
    public String getElectionType() { return electionType; }
    public int getRegisteredVoters() { return registeredVoters; }
    public List<Controller> getControllers() { return controllers; }
    public VotingResult getVotingResult() { return votingResult; }
    public boolean isNeedsReentry() { return needsReentry; }
    
    public void addController(Controller controller) {
        controllers.add(controller);
    }
    
    public void setVotingResult(VotingResult result) {
        this.votingResult = result;
    }
    
    public void setNeedsReentry(boolean needsReentry) {
        this.needsReentry = needsReentry;
    }
    
    public boolean hasMinimumControllers() {
        return controllers.size() >= 2;
    }
    
    public int getControllersCount() {
        return controllers.size();
    }
}