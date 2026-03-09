# Election System

Distribuirani sistem za pracenje rezultata izbora zasnovan na ZooKeeper-u za koordinaciju i gRPC-u za komunikaciju. 
Sistem podrzava toleranciju na greske (Fault Tolerance) kroz replikaciju logova i izbor lidera.

## Arhitektura

ZooKeeper: Upravlja izborom lidera i cuva adrese aktivnih cvorova.

ElectionAppServer: Cvorovi koji cuvaju stanje izbora. Samo Lider moze da prihvata nove glasove i replikuje ih ostalima.

ElectionClient: Simulira kontrolore na birackim mestima.

## Pokretanje

1. Pokretanje ZooKeeper-a

zkServer

2. Kompajliranje projekta

U run configurations:

Base directory: ${project_loc:PDS-Election-System}

goals: clean package

3. Pokretanje 3 servera da bi testirali FT

java -cp "target/PDS-Election-System-0.0.1-SNAPSHOT-jar-with-dependencies.jar" rs.fink.pds.election.server.ElectionAppServer 127.0.0.1:2181 50051 election_log_1.txt snapshots/


java -cp "target/PDS-Election-System-0.0.1-SNAPSHOT-jar-with-dependencies.jar" rs.fink.pds.election.server.ElectionAppServer 127.0.0.1:2181 50052 election_log_2.txt snapshots/


java -cp "target/PDS-Election-System-0.0.1-SNAPSHOT-jar-with-dependencies.jar" rs.fink.pds.election.server.ElectionAppServer 127.0.0.1:2181 50053 election_log_3.txt snapshots/

4. Pokretanje klijenta

java -Djava.net.preferIPv4Stack=true -cp "target/PDS-Election-System-0.0.1-SNAPSHOT-jar-with-dependencies.jar" rs.fink.pds.election.client.ElectionClient 127.0.0.1:2181


### Majority Consensus

Da bi rezultati sa birackog mesta bili prihvaceni u zvanicnu statistiku, dva razlicita kontrolora moraju poslati identicne podatke za isto biracko mesto.

### Fault Tolerance

Sistem koristi Raft-like replikaciju logova:

Klijent salje zahtev Lideru.

Lider upisuje komandu u svoj lokalni log.

Lider salje komandu svim pratiocima (Followers).

Kada vecina potvrdi prijem, komanda se izvrsava na stanje izbora.

### Snapshotting

Da bi se sprecilo prekomerno rastezanje log fajlova, sistem automatski kreira Snapshot stanja nakon odredjenog broja komandi, omogucavajuci brzi oporavak cvorova nakon restarta.
