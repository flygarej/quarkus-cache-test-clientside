package eu.flygare.quarkus.test;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import java.time.Duration;
import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class GreetingResourceTest {


    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/test/hello")
                .then()
                .statusCode(200)
                .body(is("Hello from RESTEasy Reactive"));
    }

    //@Test
    public void testTokenSynch() {
        given()
                .when().get("/test/tokentest")
                .then()
                .statusCode(200)
                .body(is("newtoken"));
    }


//    @Test
//    public void handlerTestSimple() {
//        tokensource.resetToken();
//        String token = tokenhandler.getToken().await().atMost(Duration.ofSeconds(30));
//        assertEquals("newtoken", token);
//        token = tokenhandler.getToken().await().atMost(Duration.ofSeconds(30));
//        assertEquals("newtoken", token);
//        tokenhandler.invalidateToken();
//        token = tokenhandler.getToken().await().atMost(Duration.ofSeconds(30));
//        assertEquals("extratoken", token);
//    }

}
