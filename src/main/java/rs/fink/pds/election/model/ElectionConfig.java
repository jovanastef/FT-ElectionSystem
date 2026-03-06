package rs.fink.pds.election.model;

import java.util.ArrayList;
import java.util.List;

public class ElectionConfig {
    private final List<String> presidentialCandidates;
    private final List<String> parliamentaryLists;
    private final List<String> localCandidates;
    private final int numberOfPollingStations;
    private final int registeredVotersPerStation;
    
    public ElectionConfig() {
        this.presidentialCandidates = new ArrayList<>();
        this.parliamentaryLists = new ArrayList<>();
        this.localCandidates = new ArrayList<>();
        this.numberOfPollingStations = 1000;
        this.registeredVotersPerStation = 1000;
    }
    
    public List<String> getPresidentialCandidates() { return presidentialCandidates; }
    public List<String> getParliamentaryLists() { return parliamentaryLists; }
    public List<String> getLocalCandidates() { return localCandidates; }
    public int getNumberOfPollingStations() { return numberOfPollingStations; }
    public int getRegisteredVotersPerStation() { return registeredVotersPerStation; }
    
    public void addPresidentialCandidate(String candidate) {
        presidentialCandidates.add(candidate);
    }
    
    public void addParliamentaryList(String list) {
        parliamentaryLists.add(list);
    }
    
    public void addLocalCandidate(String candidate) {
        localCandidates.add(candidate);
    }
}