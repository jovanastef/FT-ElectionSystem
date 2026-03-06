package rs.fink.pds.election.model;

import java.util.HashMap;
import java.util.Map;

public class ElectionState {
    private final Map<Integer, PollingStation> pollingStations;
    private final ElectionConfig config;
    private long lastSnapshotIndex = 0;
    
    public ElectionState() {
        this.pollingStations = new HashMap<>();
        this.config = new ElectionConfig();
    }
    
    public Map<Integer, PollingStation> getPollingStations() { return pollingStations; }
    public ElectionConfig getConfig() { return config; }
    public long getLastSnapshotIndex() { return lastSnapshotIndex; }
    
    public void addPollingStation(PollingStation station) {
        pollingStations.put(station.getStationId(), station);
    }
    
    public PollingStation getPollingStation(int stationId) {
        return pollingStations.get(stationId);
    }
    
    public void setLastSnapshotIndex(long index) {
        this.lastSnapshotIndex = index;
    }
    
    public int getTotalStations() {
        return pollingStations.size();
    }
    
    public int getStationsWithResults() {
        int count = 0;
        for (PollingStation station : pollingStations.values()) {
            if (station.getVotingResult() != null) {
                count++;
            }
        }
        return count;
    }
    
    public int getStationsNeedingReentry() {
        int count = 0;
        for (PollingStation station : pollingStations.values()) {
            if (station.isNeedsReentry()) {
                count++;
            }
        }
        return count;
    }
}