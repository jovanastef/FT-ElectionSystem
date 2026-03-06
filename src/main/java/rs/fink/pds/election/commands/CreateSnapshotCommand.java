package rs.fink.pds.election.commands;

import java.nio.charset.StandardCharsets;

public class CreateSnapshotCommand extends Command {
    private final long snapshotIndex;
    
    public CreateSnapshotCommand(long snapshotIndex) {
        super(CommandType.CREATE_SNAPSHOT);
        this.snapshotIndex = snapshotIndex;
    }
    
    public long getSnapshotIndex() { return snapshotIndex; }
    
    @Override
    public String writeToString() {
        return String.format("CREATE_SNAPSHOT|%d", snapshotIndex);
    }
    
    @Override
    public byte[] serialize() {
        return writeToString().getBytes(StandardCharsets.UTF_8);
    }
    
    public static CreateSnapshotCommand deserialize(String data) {
        String[] parts = data.split("\\|");
        return new CreateSnapshotCommand(Long.parseLong(parts[1]));
    }
}