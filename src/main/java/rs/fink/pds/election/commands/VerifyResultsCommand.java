package rs.fink.pds.election.commands;

import java.nio.charset.StandardCharsets;

public class VerifyResultsCommand extends Command {
    private final int pollingStationId;
    
    public VerifyResultsCommand(int pollingStationId) {
        super(CommandType.VERIFY_RESULTS);
        this.pollingStationId = pollingStationId;
    }
    
    public int getPollingStationId() { return pollingStationId; }
    
    @Override
    public String writeToString() {
        return String.format("VERIFY_RESULTS|%d", pollingStationId);
    }
    
    @Override
    public byte[] serialize() {
        return writeToString().getBytes(StandardCharsets.UTF_8);
    }
    
    public static VerifyResultsCommand deserialize(String data) {
        String[] parts = data.split("\\|");
        return new VerifyResultsCommand(Integer.parseInt(parts[1]));
    }
}