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

    // I use atomicbooleans to guard cache write and invalidate
    // This can most probably be improved.
    AtomicBoolean pendingUniActive = new AtomicBoolean(false);
    AtomicBoolean isWaitingForToken = new AtomicBoolean(false);
    // We store the first Uni when we are not cached in a global guarded by an AtomicBolean
    Uni<String> pendingUni = null;

    /**
     * Cache the uni by returning the pending one
     *
     * @return
     */
    @CacheResult(cacheName = "connectapi-token-rest")
    public Uni<String> restGetToken() {
        // Cache annotation can be here or on the REST interface.
        // README:
        // We must ensure that pending unis are not created multiple times.
        return getPendingUni();

    }

    /**
     * Naive reactive cache as seen in guides
     *
     * @return
     */
    @CacheResult(cacheName = "connectapi-token-rest-naive")
    public Uni<String> restGetTokenNaive() {
        // Cache annotation can be here or on the REST interface.
        // README:
        // We must ensure that pending unis are not created multiple times.
        return tokensource.getToken().memoize().indefinitely()
                .log("getRestTokenUniNaive:");

    }

    /**
     * Get pending Uni, if none found create one and memoize Not sure the lock
     * is needed...
     *
     * @return
     */
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

    /**
     * Invalidation of reactive done in two steps, first manipulate the AtomicBoolean,
     * unless we're waiting for an ongoing resolution, then call
     * invalidateCache() and let annotation zap cache entry.
     * Could probably mess with cache directly, but nah.
     * @return
     */
    public Uni<Boolean> invalidateBoolean() {
        // This code is a mess wrt thread safety, but is good enough to show what I want
        return Uni.createFrom().item(isWaitingForToken.get()).map((isWaiting) -> {
            LOG.info("waiting for token: " + isWaiting);
            if (isWaiting) {
                return false;
            } // We will NOT invalidate
            else { // Check if we have a pending UNI that needs to be reset
                LOG.info("Pending uni: " + pendingUniActive.get());
                if (pendingUniActive.compareAndSet(true, false)) {
                    pendingUni = null; // Just to ensure we d not leave an old Uni in global
                }
                LOG.info("Pending uni: " + pendingUniActive.get());
                return true;
            }
        }
        );
    }

    /**
     * Zap reactive cache
     * @return 
     */
    @CacheInvalidateAll(cacheName = "connectapi-token-rest")
    public Boolean invalidateCache() {
        LOG.info("cache invalidation triggered");
        return true;
    }

    /**
     * Normal caching of blocking calls
     *
     * @return
     */
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

}
