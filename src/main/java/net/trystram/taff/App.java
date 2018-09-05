package net.trystram.taff;

import io.vertx.core.*;
import io.vertx.core.dns.AddressResolverOptions;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.service.credentials.CredentialsAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.TenantAmqpEndpoint;
import org.eclipse.kapua.hono.*;

import org.eclipse.hono.service.auth.AuthenticationService;

public class App extends AbstractVerticle{


     Vertx vertx;


    // registry apps (verticles) that will listen and responds to vertx events
     KapuaRegistrationService registrationService;
     KapuaCredentialsService credentialsService;
     KapuaTenantService tenantService;
     AuthenticationService authenticationService;

    /**
     * Initialize the singleton Vert.x instance to be used.
     *
     * @return the instance.
     */
    public  Vertx createVertx() {
        final VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(new AddressResolverOptions()
                        .setCacheNegativeTimeToLive(0) // discard failed DNS lookup results immediately
                        .setCacheMaxTimeToLive(0) // support DNS based service resolution
                        .setQueryTimeout(1000));

        new VertxProperties().configureVertx(options);

        return Vertx.vertx(options);
    }

    private  Future<String> deployCredentialsService() {
        final Future<String> result = Future.future();
        System.out.println("Starting credentials service ");
        vertx.deployVerticle(credentialsService, result.completer());
        return result;
    }

    private  Future<String> deployAuthenticationService() {
        final Future<String> result = Future.future();
        if (!Verticle.class.isInstance(authenticationService)) {
            result.fail("authentication service is not a verticle");
        } else {
            System.out.println("Starting authentication service ");
            vertx.deployVerticle((Verticle) authenticationService, result.completer());
        }
        return result;
    }

    private  Future<String> deployRegistrationService() {
        final Future<String> result = Future.future();
        System.out.println("Starting registration service ");
        vertx.deployVerticle(registrationService, result.completer());
        return result;
    }

    private  Future<String> deployTenantService() {
        final Future<String> result = Future.future();
        System.out.println("Starting tenant service ");
        vertx.deployVerticle(tenantService, result.completer());
        return result;
    }


    @Override
    public void start(Future<Void> fut) {


        // create an vertx instance
        //vertx = createVertx();
        // get the local instance
        vertx = getVertx();


        //Set up AMQP endpoints
        // these instances will expose AMQP servers and forward incoming AMQP messages on the vertx Event bus

        RegistrationAmqpEndpoint registrationEndpoint = new RegistrationAmqpEndpoint(vertx);
        TenantAmqpEndpoint tenantEndpoint = new TenantAmqpEndpoint(vertx);
        CredentialsAmqpEndpoint credentialEndpoint = new CredentialsAmqpEndpoint(vertx);


        // Configure the endpoints
        ServiceConfigProperties registrationProps = new ServiceConfigProperties();
        registrationProps.setInsecurePort(2627);
        registrationProps.setPort(2626);
        registrationProps.setBindAddress("0.0.0.0");
        registrationEndpoint.setConfiguration(registrationProps);

        registrationEndpoint.start();
        System.out.println(registrationEndpoint.getName() + "is started");

        // start the actual registry applications
        final Future<Void> result = Future.future();
        CompositeFuture.all(
                deployAuthenticationService(), // we only need 1 authentication service
                deployTenantService(),
                deployRegistrationService(),
                deployCredentialsService()).setHandler(ar -> {
            if (ar.succeeded()) {
                result.complete();
            } else {
                result.fail(ar.cause());
            }
        });
    }
}
