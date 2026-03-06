package rs.fink.pds.election.commands;

import java.io.Serializable;

public abstract class Command implements Serializable {
    public enum CommandType { 
        ADD_VOTING_RESULT, 
        VERIFY_RESULTS, 
        GET_STATISTICS,
        CREATE_SNAPSHOT,
        LOAD_SNAPSHOT
    }
    
    protected final CommandType type;
    protected final long timestamp;
    
    public Command(CommandType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    public CommandType getType() { return type; }
    public long getTimestamp() { return timestamp; }
    
    public abstract String writeToString();
    public abstract byte[] serialize();
}