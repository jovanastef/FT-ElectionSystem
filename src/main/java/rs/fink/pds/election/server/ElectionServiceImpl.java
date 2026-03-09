package rs.fink.pds.election.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import rs.fink.pds.election.model.*;

public class ElectionServiceImpl {
    private final ElectionState electionState;
    private final Map<Integer, Map<String, VotingResult>> stationControllerResults;
    
    public ElectionServiceImpl() {
        this.electionState = new ElectionState();
        this.stationControllerResults = new ConcurrentHashMap<>();
        initializePollingStations();
    }
    
    private void initializePollingStations() {
        ElectionConfig config = electionState.getConfig();
        for (int i = 1; i <= config.getNumberOfPollingStations(); i++) {
            PollingStation station = new PollingStation(i, "parlamentarni", config.getRegisteredVotersPerStation());
            electionState.addPollingStation(station);
            
            // Dodaj 2-3 kontrolora po mestu
            for (int j = 1; j <= 3; j++) {
                Controller controller = new Controller("C" + i + "_" + j, "Controller " + j, i);
                station.addController(controller);
            }
        }
    }
    
    public synchronized boolean addVotingResult(int pollingStationId, String controllerId, 
                                                int totalVoters, int invalidBallots, 
                                                Map<String, Integer> candidateVotes,
                                                Map<String, Integer> listVotes) {
    	System.out.println("DEBUG [Service]: Adding result - station=" + pollingStationId + 
                ", controller=" + controllerId);
    	
    	PollingStation station = electionState.getPollingStation(pollingStationId);
        if (station == null) {
            System.err.println("Polling station not found: " + pollingStationId);
            return false;
        }
        
        // Kreiraj rezultat za ovog kontrolora
        VotingResult result = new VotingResult(pollingStationId, totalVoters, invalidBallots, 1);
        for (Map.Entry<String, Integer> entry : candidateVotes.entrySet()) {
            result.addCandidateVote(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : listVotes.entrySet()) {
            result.addListVote(entry.getKey(), entry.getValue());
        }
        
        // Sacuvaj rezultat po kontroloru
        stationControllerResults.computeIfAbsent(pollingStationId, k -> new HashMap<>())
                                .put(controllerId, result);
        
        // Proveri da li imamo vecinu kontrolora
        Map<String, VotingResult> controllerResults = stationControllerResults.get(pollingStationId);
        System.out.println("DEBUG [Service]: Controller count for station " + pollingStationId + 
                " = " + controllerResults.size());
        if (controllerResults.size() >= 2) {
            // Verifikuj da li se rezultati poklapaju
            if (verifyResultsMajority(pollingStationId)) {
                station.setVotingResult(result);
                station.setNeedsReentry(false);
                System.out.println("Results verified for station: " + pollingStationId);
                return true;
            } else {
                station.setNeedsReentry(true);
                System.err.println("Results mismatch at station: " + pollingStationId);
                return false;
            }
        }
        
        System.out.println("Waiting for more controllers at station: " + pollingStationId + 
                " (have " + controllerResults.size() + "/2)");
        return true;
    }
    
    private boolean verifyResultsMajority(int pollingStationId) {
        Map<String, VotingResult> controllerResults = stationControllerResults.get(pollingStationId);
        if (controllerResults == null || controllerResults.size() < 2) {
            return false;
        }
        
        List<VotingResult> results = new ArrayList<>(controllerResults.values());
        VotingResult first = results.get(0);
        
        for (int i = 1; i < results.size(); i++) {
            if (!first.resultsMatch(results.get(i))) {
                return false;
            }
        }
        
        return true;
    }
    
    public ElectionStatistics getStatistics(String electionType) {
        ElectionStatistics stats = new ElectionStatistics();
        stats.totalStations = electionState.getTotalStations();
        stats.stationsWithResults = electionState.getStationsWithResults();
        stats.stationsNeedingReentry = electionState.getStationsNeedingReentry();
        
        int totalVoters = 0;
        int totalValidVotes = 0;
        Map<String, Integer> candidateTotals = new HashMap<>();
        Map<String, Integer> listTotals = new HashMap<>();
        
        for (PollingStation station : electionState.getPollingStations().values()) {
            VotingResult result = station.getVotingResult();
            if (result != null) {
                totalVoters += result.getTotalVoters();
                totalValidVotes += result.getValidBallots();
                
                for (Map.Entry<String, Integer> entry : result.getCandidateVotes().entrySet()) {
                    candidateTotals.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                for (Map.Entry<String, Integer> entry : result.getListVotes().entrySet()) {
                    listTotals.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
        }
        
        stats.totalRegisteredVoters = totalVoters;
        stats.totalValidVotes = totalValidVotes;
        stats.turnoutPercentage = totalVoters > 0 ? (totalValidVotes * 100.0 / totalVoters) : 0;
        stats.candidateResults = candidateTotals;
        stats.listResults = listTotals;
        
        return stats;
    }
    
    public List<Integer> getStationsNeedingReentry() {
        List<Integer> stations = new ArrayList<>();
        for (PollingStation station : electionState.getPollingStations().values()) {
            if (station.isNeedsReentry()) {
                stations.add(station.getStationId());
            }
        }
        return stations;
    }
    
    public ElectionState getElectionState() {
        return electionState;
    }
    
    public void loadFromSnapshot(ElectionState state) {
        System.out.println("Loading election state from snapshot...");
        
        // Kopiraj polling stations iz snapshot-a
        this.electionState.getPollingStations().clear();
        for (Map.Entry<Integer, PollingStation> entry : state.getPollingStations().entrySet()) {
            // Kreiraj novu instancu da izbegnem reference
            PollingStation original = entry.getValue();
            PollingStation copy = new PollingStation(
                original.getStationId(),
                original.getElectionType(),
                original.getRegisteredVoters()
            );
            copy.setVotingResult(original.getVotingResult());
            copy.setNeedsReentry(original.isNeedsReentry());
            this.electionState.addPollingStation(copy);
        }
        
        // Kopiraj config
        this.electionState.getConfig().getPresidentialCandidates()
            .addAll(state.getConfig().getPresidentialCandidates());
        // dodaj ostale liste po potrebi
        
        System.out.println("Election state loaded. Stations with results: " + 
                          this.electionState.getStationsWithResults());
    }
    
    public static class ElectionStatistics {
        public int totalStations;
        public int stationsWithResults;
        public int stationsNeedingReentry;
        public int totalRegisteredVoters;
        public int totalValidVotes;
        public double turnoutPercentage;
        public Map<String, Integer> candidateResults = new HashMap<>();
        public Map<String, Integer> listResults = new HashMap<>();
    }
}
