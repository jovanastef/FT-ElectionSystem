package rs.fink.pds.election.model;

import java.util.HashMap;
import java.util.Map;

public class VotingResult {
    private final int pollingStationId;
    private final int totalVoters;
    private final int invalidBallots;
    private final Map<String, Integer> candidateVotes; // candidate name -> votes
    private final Map<String, Integer> listVotes; // list name -> votes
    private final int controllerCount;
    
    public VotingResult(int pollingStationId, int totalVoters, int invalidBallots, int controllerCount) {
        this.pollingStationId = pollingStationId;
        this.totalVoters = totalVoters;
        this.invalidBallots = invalidBallots;
        this.controllerCount = controllerCount;
        this.candidateVotes = new HashMap<>();
        this.listVotes = new HashMap<>();
    }
    
    public int getPollingStationId() { return pollingStationId; }
    public int getTotalVoters() { return totalVoters; }
    public int getInvalidBallots() { return invalidBallots; }
    public int getValidBallots() { return totalVoters - invalidBallots; }
    public int getControllerCount() { return controllerCount; }
    public Map<String, Integer> getCandidateVotes() { return candidateVotes; }
    public Map<String, Integer> getListVotes() { return listVotes; }
    
    public void addCandidateVote(String candidateName, int votes) {
        candidateVotes.put(candidateName, votes);
    }
    
    public void addListVote(String listName, int votes) {
        listVotes.put(listName, votes);
    }
    
    public int getCandidateVotes(String candidateName) {
        return candidateVotes.getOrDefault(candidateName, 0);
    }
    
    public int getListVotes(String listName) {
        return listVotes.getOrDefault(listName, 0);
    }
    
    public boolean resultsMatch(VotingResult other) {
        if (other == null) return false;
        if (this.totalVoters != other.totalVoters) return false;
        if (this.invalidBallots != other.invalidBallots) return false;
        
        for (String candidate : this.candidateVotes.keySet()) {
            if (!this.candidateVotes.get(candidate).equals(other.candidateVotes.get(candidate))) {
                return false;
            }
        }
        
        for (String list : this.listVotes.keySet()) {
            if (!this.listVotes.get(list).equals(other.listVotes.get(list))) {
                return false;
            }
        }
        
        return true;
    }
}