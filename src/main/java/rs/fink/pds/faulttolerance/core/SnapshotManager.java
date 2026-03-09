package rs.fink.pds.faulttolerance.core;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import rs.fink.pds.election.model.ElectionState;

public class SnapshotManager {
    private static final String SNAPSHOT_DIR = "snapshots/";
    private static final String SNAPSHOT_PREFIX = "snapshot_";
    //private static final long SNAPSHOT_THRESHOLD = 1000; // commands before snapshot
    private static final long SNAPSHOT_THRESHOLD = 5; // 5 za testiranje
    
    private final String baseDir;
    private long lastSnapshotIndex = 0;
    
    public SnapshotManager(String baseDir) {
        this.baseDir = baseDir;
        createSnapshotDirectory();
    }
    
    private void createSnapshotDirectory() {
        try {
            Files.createDirectories(Paths.get(baseDir + SNAPSHOT_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create snapshot directory: " + e.getMessage());
        }
    }
    
    public synchronized void createSnapshot(ElectionState state, long logIndex) {
        String fileName = baseDir + SNAPSHOT_DIR + SNAPSHOT_PREFIX + logIndex + ".dat";
        
        try (FileOutputStream fos = new FileOutputStream(fileName);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GZIPOutputStream gzos = new GZIPOutputStream(bos);
             ObjectOutputStream oos = new ObjectOutputStream(gzos)) {
            
            oos.writeObject(state);
            oos.flush();
            
            lastSnapshotIndex = logIndex;
            state.setLastSnapshotIndex(logIndex);
            
            System.out.println("Snapshot created at index: " + logIndex);
            
            // Cleanup old snapshots (keep last 3)
            cleanupOldSnapshots();
            
        } catch (IOException e) {
            System.err.println("Failed to create snapshot: " + e.getMessage());
        }
    }
    
    public synchronized ElectionState loadLatestSnapshot() {
        File snapshotDir = new File(baseDir + SNAPSHOT_DIR);
        File[] snapshotFiles = snapshotDir.listFiles(
            (dir, name) -> name.startsWith(SNAPSHOT_PREFIX) && name.endsWith(".dat")
        );
        
        if (snapshotFiles == null || snapshotFiles.length == 0) {
            System.out.println("No snapshots found, starting fresh");
            return new ElectionState();
        }
        
        // Find latest snapshot
        File latestSnapshot = null;
        long maxIndex = -1;
        
        for (File file : snapshotFiles) {
            try {
                String fileName = file.getName();
                long index = Long.parseLong(
                    fileName.substring(SNAPSHOT_PREFIX.length(), fileName.length() - 4)
                );
                if (index > maxIndex) {
                    maxIndex = index;
                    latestSnapshot = file;
                }
            } catch (NumberFormatException e) {
                // Skip invalid files
            }
        }
        
        if (latestSnapshot != null) {
            try (FileInputStream fis = new FileInputStream(latestSnapshot);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 GZIPInputStream gzis = new GZIPInputStream(bis);
                 ObjectInputStream ois = new ObjectInputStream(gzis)) {
                
                ElectionState state = (ElectionState) ois.readObject();
                lastSnapshotIndex = maxIndex;
                System.out.println("Loaded snapshot from index: " + maxIndex);
                return state;
                
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Failed to load snapshot: " + e.getMessage());
            }
        }
        
        return new ElectionState();
    }
    
    public long getLastSnapshotIndex() {
        return lastSnapshotIndex;
    }
    
    public boolean shouldCreateSnapshot(long currentIndex) {
        return (currentIndex - lastSnapshotIndex) >= SNAPSHOT_THRESHOLD;
    }
    
    private void cleanupOldSnapshots() {
        File snapshotDir = new File(baseDir + SNAPSHOT_DIR);
        File[] snapshotFiles = snapshotDir.listFiles(
            (dir, name) -> name.startsWith(SNAPSHOT_PREFIX) && name.endsWith(".dat")
        );
        
        if (snapshotFiles != null && snapshotFiles.length > 3) {
            // Sort by index and delete oldest
            java.util.Arrays.sort(snapshotFiles, (f1, f2) -> {
                long i1 = extractIndex(f1.getName());
                long i2 = extractIndex(f2.getName());
                return Long.compare(i1, i2);
            });
            
            for (int i = 0; i < snapshotFiles.length - 3; i++) {
                snapshotFiles[i].delete();
                System.out.println("Deleted old snapshot: " + snapshotFiles[i].getName());
            }
        }
    }
    
    private long extractIndex(String fileName) {
        try {
            return Long.parseLong(
                fileName.substring(SNAPSHOT_PREFIX.length(), fileName.length() - 4)
            );
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}