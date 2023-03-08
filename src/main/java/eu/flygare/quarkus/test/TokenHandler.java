package eu.flygare.quarkus.test;

import io.quarkus.arc.Lock;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 *
 * @author Jonas Flygare <jonas.flygare@teliacompany.com.com>
 */
@ApplicationScoped
public class TokenHandler {

    public final static Logger LOG = Logger.getLogger(TokenHandler.class);
    // Class handles login token and ensures token renewal is only done by one of the threads
    // accessing the token.

    // The token server resides in project ace-cci-sandbox-recording-tokenservice
    @RestClient
    TokenSource tokensource;

// We can access the cache directly, but if we instead make sure there is only one 
// Uni returned things work as we want...
//    @Inject
//    @CacheName("connectapi-token-rest")
//    Cache cache;
//    
//    @Inject
//    CacheManager cacheManager;
    AtomicBoolean pendingUniActive = new AtomicBoolean(false);
    AtomicBoolean isWaitingForToken = new AtomicBoolean(false);
    Uni<String> pendingUni = null;

    @CacheResult(cacheName = "connectapi-token-rest")
    public Uni<String> restGetToken() {
        // Cache annotation can be here or on the REST interface.
        // README:
        // We must ensure that pending unis are not created multiple times.
        return getPendingUni();

    }
    
    
    @CacheResult(cacheName = "connectapi-token-rest-naive")
    public Uni<String> restGetTokenNaive() {
        // Cache annotation can be here or on the REST interface.
        // README:
        // We must ensure that pending unis are not created multiple times.
        return tokensource.getToken().memoize().indefinitely()
                    .log("getRestTokenUniNaive:");

    }

    // Wrap with lock to make sure only one thread at a time access the boolean.
    @Lock(Lock.Type.WRITE)
    public Uni<String> getPendingUni() {
        LOG.info("Fetching uni. boolean= " + pendingUniActive.get());
        if (pendingUniActive.compareAndSet(false, true)) {
            isWaitingForToken.set(true);
            pendingUni = tokensource.getToken().memoize().indefinitely()
                    .log("getRestTokenUni:").onItem().invoke(() -> {
                isWaitingForToken.set(false);
                LOG.info("Not waiting any longer");
            });
        }
        LOG.info("Fetching uni. boolean= " + pendingUniActive.get());
        return pendingUni;
    }

    @CacheInvalidateAll(cacheName = "connectapi-token-rest")
    public Boolean invalidateCache() {
        LOG.info("cache invalidation triggered");
        return true;
    }

    public Uni<Boolean> invalidateBoolean() {
        return Uni.createFrom().item(isWaitingForToken.get()).map((isWaiting) -> {
            LOG.info("waiting for token: " + isWaiting);
            if (isWaiting) {
                return false;
            } // We will NOT invalidate
            else { // Check if we have a pending UNI that needs to be reset
                LOG.info("Pending uni: " + pendingUniActive.get());
                if (pendingUniActive.compareAndSet(true, false)) {
                    pendingUni = null;
                }
                LOG.info("Pending uni: " + pendingUniActive.get());
                return true;
            }
        }
        );
    }

    @CacheResult(cacheName = "connectapi-token-rest-blocking")
    public String restGetTokenBlocking() {
        // Cache annotation can be here or on the REST interface.
        // README:
        // It is vital that the rest service is written as a "normal" service, that is
        // it returns entities as usual. 
        // If you declare the server side method as Uni<Entity> caching will break
        // horribly... 
        return tokensource.getTokenBlocking();
    }

//    // Old method manually protecting token, deprecated in favor of guarded cache handling
//    @Lock(value = Lock.Type.WRITE, unit = TimeUnit.SECONDS, time = 20)
//    public String getTokenHomeRolled() {
//        // Check if token is invalid. If so get new one. Surrounding lock will queue other threads until resolved.
//        if (tokenOk.compareAndSet(false, true)) {
//            // This part should be atomic, blocking others from entering test above until done.
//            token = Uni.createFrom().item("foobar").map((t) -> {
//                // If here, tokenOk=false!
//                LOG.info("Delaying return of token, simulating time to fetch new one");
//                return tokensource.getRestToken();
//            })
//                    .onItem().delayIt().by(Duration.ofSeconds(10))
//                    .invoke(() -> {
//                        tokenOk.set(true); // Do not set this until AFTER delay!
//                    }).await().atMost(Duration.ofSeconds(30));
//        }
//        return token;
//
//    }
}
