package rs.fink.pds.election.commands;

import java.nio.charset.StandardCharsets;

public class GetStatisticsCommand extends Command {
    private final String electionType;
    
    public GetStatisticsCommand(String electionType) {
        super(CommandType.GET_STATISTICS);
        this.electionType = electionType;
    }
    
    public String getElectionType() { return electionType; }
    
    @Override
    public String writeToString() {
        return String.format("GET_STATISTICS|%s", electionType);
    }
    
    @Override
    public byte[] serialize() {
        return writeToString().getBytes(StandardCharsets.UTF_8);
    }
    
    public static GetStatisticsCommand deserialize(String data) {
        String[] parts = data.split("\\|");
        return new GetStatisticsCommand(parts[1]);
    }
}