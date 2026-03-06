package rs.fink.pds.election.commands;

import java.nio.charset.StandardCharsets;

public class AddVotingResultCommand extends Command {
    private final int pollingStationId;
    private final String controllerId;
    private final int totalVoters;
    private final int invalidBallots;
    private final String electionType;
    
    public AddVotingResultCommand(int pollingStationId, String controllerId, 
                                   int totalVoters, int invalidBallots, String electionType) {
        super(CommandType.ADD_VOTING_RESULT);
        this.pollingStationId = pollingStationId;
        this.controllerId = controllerId;
        this.totalVoters = totalVoters;
        this.invalidBallots = invalidBallots;
        this.electionType = electionType;
    }
    
    public int getPollingStationId() { return pollingStationId; }
    public String getControllerId() { return controllerId; }
    public int getTotalVoters() { return totalVoters; }
    public int getInvalidBallots() { return invalidBallots; }
    public int getValidBallots() { return totalVoters - invalidBallots; }
    public String getElectionType() { return electionType; }
    
    @Override
    public String writeToString() {
        return String.format("ADD_VOTING_RESULT|%d|%s|%d|%d|%s",
            pollingStationId, controllerId, totalVoters, invalidBallots, electionType);
    }
    
    @Override
    public byte[] serialize() {
        return writeToString().getBytes(StandardCharsets.UTF_8);
    }
    
    public static AddVotingResultCommand deserialize(String data) {
        String[] parts = data.split("\\|");
        return new AddVotingResultCommand(
            Integer.parseInt(parts[1]),
            parts[2],
            Integer.parseInt(parts[3]),
            Integer.parseInt(parts[4]),
            parts[5]
        );
    }
}