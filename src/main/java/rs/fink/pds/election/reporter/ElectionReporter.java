package rs.fink.pds.election.reporter;

import java.util.*;
import java.util.stream.Collectors;
import rs.fink.pds.election.server.ElectionServiceImpl;
import rs.fink.pds.election.server.ElectionServiceImpl.ElectionStatistics;
import rs.fink.pds.election.model.PollingStation;
import rs.fink.pds.election.model.VotingResult;

public class ElectionReporter {
    private final ElectionServiceImpl electionService;
    
    public ElectionReporter(ElectionServiceImpl service) {
        this.electionService = service;
    }
    
    /**
     * Generise izvestaj o izlaznosti biraca
     */
    public String generateTurnoutReport() {
        ElectionStatistics stats = electionService.getStatistics("all");
        
        StringBuilder report = new StringBuilder();
        report.append("===  IZBORNI IZVESTAJ - IZLAZNOST ===\n");
        report.append("Ukupno birackih mesta: ").append(stats.totalStations).append("\n");
        report.append("Obradjeno mesta: ").append(stats.stationsWithResults).append("\n");
        report.append("Mesta za ponovni unos: ").append(stats.stationsNeedingReentry).append("\n\n");
        report.append("Ukupno registrovanih biraca: ").append(stats.totalRegisteredVoters).append("\n");
        report.append("Vazeci listici: ").append(stats.totalValidVotes).append("\n");
        report.append("Izlaznost: ").append(String.format("%.2f", stats.turnoutPercentage)).append("%\n");
        
        return report.toString();
    }
    
    /**
     * Generise rezultate po predsednickim kandidatima
     */
    public String generatePresidentialResults() {
        ElectionStatistics stats = electionService.getStatistics("presidential");
        
        StringBuilder report = new StringBuilder();
        report.append("===  REZULTATI - PREDSEDNICKI KANDIDATI ===\n\n");
        
        if (stats.candidateResults.isEmpty()) {
            report.append("Nema unetih rezultata jos uvek.\n");
            return report.toString();
        }
        
        // Sortiraj kandidate po broju glasova (opadajuce)
        List<Map.Entry<String, Integer>> sorted = stats.candidateResults.entrySet()
            .stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());
        
        int rank = 1;
        int totalVotes = stats.candidateResults.values().stream().mapToInt(Integer::intValue).sum();
        
        for (Map.Entry<String, Integer> entry : sorted) {
            double percentage = totalVotes > 0 ? (entry.getValue() * 100.0 / totalVotes) : 0;
            report.append(String.format("%d. %-30s %6d glasova (%.2f%%)\n", 
                rank++, entry.getKey(), entry.getValue(), percentage));
        }
        
        report.append("\nUkupno glasova za kandidate: ").append(totalVotes).append("\n");
        
        return report.toString();
    }
    
    /**
     * Generise rezultate po parlamentarnim listama
     */
    public String generateParliamentaryResults() {
        ElectionStatistics stats = electionService.getStatistics("parliamentary");
        
        StringBuilder report = new StringBuilder();
        report.append("=== REZULTATI - PARLAMENTARNE LISTE ===\n\n");
        
        if (stats.listResults.isEmpty()) {
            report.append("Nema unetih rezultata još uvek.\n");
            return report.toString();
        }
        
        // Sortiraj liste po broju glasova (opadajuce)
        List<Map.Entry<String, Integer>> sorted = stats.listResults.entrySet()
            .stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());
        
        int rank = 1;
        int totalVotes = stats.listResults.values().stream().mapToInt(Integer::intValue).sum();
        
        for (Map.Entry<String, Integer> entry : sorted) {
            double percentage = totalVotes > 0 ? (entry.getValue() * 100.0 / totalVotes) : 0;
            report.append(String.format("%d. %-30s %6d glasova (%.2f%%)\n", 
                rank++, entry.getKey(), entry.getValue(), percentage));
        }
        
        report.append("\nUkupno glasova za liste: ").append(totalVotes).append("\n");
        
        return report.toString();
    }
    
    /**
     * Vraca listu birackih mesta kojima je potreban ponovni unos
     */
    public List<Integer> getStationsNeedingReentry() {
        return electionService.getStationsNeedingReentry();
    }
    
    /**
     * Generise detaljan izvestaj o problematicnim mestima
     */
    public String generateReentryReport() {
        List<Integer> stations = getStationsNeedingReentry();
        
        StringBuilder report = new StringBuilder();
        report.append("=== MESTA ZA PONOVNI UNOS ===\n\n");
        
        if (stations.isEmpty()) {
            report.append("Sva biracka mesta imaju validne rezultate!\n");
            return report.toString();
        }
        
        report.append("Broj mesta sa problemom: ").append(stations.size()).append("\n\n");
        report.append("ID birackih mesta:\n");
        for (int stationId : stations) {
            report.append("  - Mesto #").append(stationId).append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * Proverava da li follower moze da servira read request-e
     * (poziva se sa follower node-a)
     */
    public boolean canServeReadRequests() {
        // Follower moze da servira read request-e ako je sinhronizovan sa liderom
        // Ovo se proverava u ReplicaNode kroz lastLogIndex tracking
        return true; // Placeholder - implementacija zavisi od ReplicaNode stanja
    }
    
    /**
     * Generise kompletan izvestaj za sve tipove izbora
     */
    public String generateFullReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔════════════════════════════════════╗\n");
        report.append("║   KOMPLETAN IZBORNI IZVESTAJ       ║\n");
        report.append("╚════════════════════════════════════╝\n\n");
        
        report.append(generateTurnoutReport()).append("\n");
        report.append(generatePresidentialResults()).append("\n");
        report.append(generateParliamentaryResults()).append("\n");
        report.append(generateReentryReport()).append("\n");
        
        return report.toString();
    }
}