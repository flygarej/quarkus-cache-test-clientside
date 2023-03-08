package eu.flygare.quarkus.test;

import io.smallrye.mutiny.Uni;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 *
 * @author Jonas Flygare <jonas.flygare@teliacompany.com>
 */
@RegisterRestClient(configKey = "tokensource")
@Path("/token")
public interface TokenSource {

    // Reactive gettoken
    @Path("/gettoken")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> getToken();
    
    // Nonreactive gettoken
    @Path("/gettokenb")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getTokenBlocking();

}
