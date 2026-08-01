package com.filmpire.movie.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Shares ONE MongoDB Testcontainer across every integration test class in
 * this module, instead of each class starting (and Ryuk having to reap) its
 * own.
 *
 * <p>Backend CI was observed failing intermittently, ~20-25 minutes into a
 * run, with every remaining Mongo-backed test throwing
 * {@code MongoTimeoutException: ... Connection refused, address=localhost:27017}
 * — the literal internal container port, never Testcontainers' actual
 * externally-mapped port. Root cause: {@code MongoDBContainer} runs
 * {@code rs.initiate()} with no host override, so the container's own
 * replica-set config self-reports its member address as {@code localhost:27017}
 * (correct from inside the container, meaningless from the test JVM). The
 * driver auto-upgrades to replica-set topology monitoring the moment it
 * sees {@code setName} in the server's {@code hello} response — regardless
 * of whether the connection string even mentions {@code replicaSet=} — so
 * it eventually acts on that self-reported address once a topology refresh
 * is triggered, at which point every further connection attempt targets a
 * port nothing outside the container can reach. {@code directConnection=true}
 * (below) tells the driver to skip replica-set discovery/monitoring
 * entirely and only ever use the one address it was given — safe here
 * because this is always a single-node replica set used purely for
 * transaction support, never a real multi-node cluster whose topology
 * actually needs discovering.
 *
 * <p>Sharing one container across classes (rather than one per class) is a
 * separate, additional improvement kept for the same underlying reason:
 * fewer container start/stop cycles is strictly better, even though it
 * turned out not to be this bug's root cause. Deliberately does NOT use
 * {@code @Container}/{@code @Testcontainers} — that combination stops the
 * container after every class. Started in a static initializer instead,
 * left running for the Testcontainers Ryuk reaper to clean up when the JVM
 * exits. Subclasses just {@code extends} this class; Spring discovers
 * {@link #mongoProperties} on the superclass automatically (a documented
 * {@code @DynamicPropertySource} behavior), so no per-subclass wiring is
 * needed for the Mongo URI itself.</p>
 */
public abstract class AbstractMongoIntegrationTest {

    protected static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer("mongo:8.0");

    static {
        MONGO_CONTAINER.start();
    }

    /**
     * Points Spring Data at the shared container's Mongo. {@code directConnection=true}
     * is load-bearing — see the class Javadoc for why replica-set topology
     * discovery must stay off for a Testcontainers single-node replica set.
     *
     * @param registry Spring test property registry
     */
    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO_CONTAINER.getReplicaSetUrl() + "?directConnection=true");
    }
}
