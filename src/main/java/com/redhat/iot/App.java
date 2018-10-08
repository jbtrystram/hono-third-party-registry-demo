package com.redhat.iot;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.auth.*;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.deviceregistry.DeviceRegistryAmqpServer;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.ClaimsBasedAuthorizationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.auth.impl.AuthenticationServerConfigProperties;
import org.eclipse.hono.service.auth.impl.FileBasedAuthenticationService;
import org.eclipse.hono.service.credentials.CredentialsAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.tenant.TenantAmqpEndpoint;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.kapua.hono.KapuaCredentialsService;
import org.eclipse.kapua.hono.KapuaRegistrationService;
import org.eclipse.kapua.hono.KapuaTenantService;
import org.springframework.core.io.FileSystemResource;

public class App {
    public static void main(String[] args) throws Exception {

        SimpleKapuaInstance.start();
        Vertx vertx = Vertx.vertx();

        // AMQP server properties
        ServiceConfigProperties registrationProps = new ServiceConfigProperties();
        registrationProps.setInsecurePort(25672);
        //registrationProps.setPort(25672);
        registrationProps.setBindAddress("0.0.0.0");
        registrationProps.setKeyStorePath("src/test/resources/certificates/deviceRegistryKeyStore.p12");
        registrationProps.setKeyStorePassword("deviceregistrykeys");

        // create an AMQP server
        DeviceRegistryAmqpServer server = new DeviceRegistryAmqpServer();
        server.setConfig(registrationProps);

        // Setup endpoints bridging AMQP to Vertx.
        // AMQP messages <-> Vert.x event bus messages.
        // Tenant endpoint
        TenantAmqpEndpoint tenantAmqpEndpoint = new TenantAmqpEndpoint(vertx);
        tenantAmqpEndpoint.setConfiguration(registrationProps);
        server.addEndpoint(tenantAmqpEndpoint);

        // Registration Endpoint
        RegistrationAmqpEndpoint registrationAmqpEndpoint = new RegistrationAmqpEndpoint(vertx);
        registrationAmqpEndpoint.setConfiguration(registrationProps);
        server.addEndpoint(registrationAmqpEndpoint);

        // credentials endpoint
        CredentialsAmqpEndpoint credentialsAmqpEndpoint = new CredentialsAmqpEndpoint(vertx);
        credentialsAmqpEndpoint.setConfiguration(registrationProps);
        server.addEndpoint(credentialsAmqpEndpoint);

        // Authorisation setup.
        AuthenticationServerConfigProperties authProps = new AuthenticationServerConfigProperties();
        authProps.setPermissionsPath(new FileSystemResource("src/main/resources/permissions.json"));
        AuthenticationService auth = new FileBasedAuthenticationService();

        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(createAuthenticationService(createUser())));
        server.setAuthorizationService(new ClaimsBasedAuthorizationService());
        vertx.deployVerticle(server);


        // Deploy the Kapua services composing the Hono Device Registry.
        // registration service
        KapuaRegistrationService registrationService = new KapuaRegistrationService();
        SignatureSupportingConfigProperties signProps = new SignatureSupportingConfigProperties();
        signProps.setKeyPath("src/main/resources/certificates/jwt/device-registry-key.pem");
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, signProps));
        vertx.deployVerticle(registrationService);

        // tenant service
        KapuaTenantService tenantService = new KapuaTenantService();
        vertx.deployVerticle(tenantService);

        //credentials service
        KapuaCredentialsService credentialsService = new KapuaCredentialsService();
        vertx.deployVerticle(credentialsService);
    }


    public static AuthenticationService createAuthenticationService(final HonoUser returnedUser) {
        return new AuthenticationService() {

            @Override
            public void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
                authenticationResultHandler.handle(Future.succeededFuture(returnedUser));
            }
        };
    }

    private static HonoUser createUser() {

        final Authorities authorities = new AuthoritiesImpl()
                // tenant authorisations
                .addResource(TenantConstants.TENANT_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE })
                .addResource(TenantConstants.TENANT_ENDPOINT, new Activity[] {Activity.READ, Activity.WRITE})
                .addOperation(TenantConstants.TENANT_ENDPOINT, "DEFAULT_TENANT", "get")
                //credentials authorisations
                .addResource(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", new Activity[] { Activity.READ, Activity.WRITE })
                .addResource(CredentialsConstants.CREDENTIALS_ENDPOINT, new Activity[] {Activity.READ, Activity.WRITE})
                .addOperation(CredentialsConstants.CREDENTIALS_ENDPOINT, "DEFAULT_TENANT", "get")
                //Device Registry authorisations
                .addResource(RegistrationConstants.REGISTRATION_ENDPOINT, "*", new Activity[] { Activity.READ, Activity.WRITE })
                .addResource(RegistrationConstants.REGISTRATION_ENDPOINT, new Activity[] {Activity.READ, Activity.WRITE})
                .addOperation(RegistrationConstants.REGISTRATION_ENDPOINT, "DEFAULT_TENANT", "get");


        return new HonoUserAdapter() {
            @Override
            public String getName() {
                return "test-client";
            }

            @Override
            public Authorities getAuthorities() {
                return authorities;
            }
        };
    }
}
