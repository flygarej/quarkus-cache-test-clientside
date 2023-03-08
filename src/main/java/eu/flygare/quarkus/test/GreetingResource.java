package eu.flygare.quarkus.test;

import io.quarkus.cache.CacheInvalidateAll;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/test")
@ApplicationScoped
public class GreetingResource {
    
    // This class is to provide a REST interface for testing caching of Entities
    // and Uni<Entity> returned from slow REST calls.
    // Issue at hand: values are not cached until Uni is resolved, and if that 
    // is slow, several calls are allowed to start concurrently...
    // We solve this by reusing the first Uni until cache have the data it 
    // needs at which point the cache mechanism will shield the actual call.

    public static final Logger LOG = Logger.getLogger(GreetingResource.class);

    @Inject
    TokenHandler tokenhandler;

    @Path("/hello")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from RESTEasy Reactive";
    }

    /**
     * Trigger call to rest service that has a 10 second delay in response
     * Uni<String> will not be resolved for 10 seconds which disables
     * the cache from intercepting
     * @return
     */
    @Path("/gettoken")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> restGetToken() { 
        return tokenhandler.restGetToken();
    }

    /**
     * Blocking call that returns string directly after 10 second delay.
     * Cache works fine since no Uni threads are created
     * @return 
     */
    @Path("/gettokenb")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String restGetTokenb() { 
        return tokenhandler.restGetTokenBlocking();
    }

    /**
     * Trigger call to rest service that has a 10 second delay in response
     * Uni<String> will not be resolved for 10 seconds which disables
     * the cache from intercepting. This call displays the bug.
     * @return
     */
    @Path("/gettokennaive")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> restGetTokenNaive() { 
        return tokenhandler.restGetTokenNaive();
    }
    
    /**
     * Invalidate cache for naive reactive calls
     * @return 
     */
    @Path("/invalidatenaive")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @CacheInvalidateAll(cacheName = "connectapi-token-rest-naive")
    public Uni<String> invalidateNaive() {
        return Uni.createFrom().item("Invalidated");
    }
    
    
    /**
     * Invalidate current cache for handled reactive calls.
     *
     * @return
     */
    @Path("/invalidate")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public Uni<String> invalidate() {
        // Two stage handling, first check booleans to see if we're waiting for a 
        // pending resolution. If not, invalidate cache.
        return tokenhandler.invalidateBoolean().map((t) -> {
            System.out.println("t: " + t);
            if (t) {
                return tokenhandler.invalidateCache();
            } else {
                return false;
            }
        })
                .map((t) -> {
                    if (t) {
                        return "invalidated";
                    } else {
                        return "not invalidated";
                    }
                });

    }
    
    @Path("/invalidateb")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @CacheInvalidateAll(cacheName = "connectapi-token-rest-blocking")
    public String invalidateCache() {
        // Must invalidate tokenhandler to make sure cached Uni is renewed in case we had several calls.
        return "Invalidated";
    }

    @Path("/error")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String error() {
        // Create error in token to simulate API problem
        return "error";
    }

}
