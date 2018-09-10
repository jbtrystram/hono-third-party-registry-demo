package net.trystram.taff;

import io.vertx.core.Vertx;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.deviceregistry.DeviceRegistryAmqpServer;
import org.eclipse.hono.service.credentials.BaseCredentialsService;
import org.eclipse.hono.service.registration.BaseRegistrationService;
import org.eclipse.hono.service.registration.RegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.tenant.BaseTenantService;
import org.eclipse.kapua.hono.KapuaCredentialsService;
import org.eclipse.kapua.hono.KapuaRegistrationService;
import org.eclipse.kapua.hono.KapuaTenantService;

public class App {

    public static void main(String[] args) throws Exception {
        Vertx vertx = Vertx.vertx();

        // AMQP server properties
        ServiceConfigProperties registrationProps = new ServiceConfigProperties();
        registrationProps.setInsecurePort(2627);
        registrationProps.setPort(2626);
        registrationProps.setBindAddress("0.0.0.0");

        DeviceRegistryAmqpServer server = new DeviceRegistryAmqpServer();
        server.setConfig(registrationProps);
        server.addEndpoint(new RegistrationAmqpEndpoint(vertx));

        vertx.deployVerticle(server);

        // registration service
        KapuaRegistrationService registrationService = new KapuaRegistrationService();
        SignatureSupportingConfigProperties signProps = new SignatureSupportingConfigProperties();
        signProps.setKeyPath("/home/jibou/github/hono/demo-certs/certs/device-registry-key.pem");
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, signProps));
        vertx.deployVerticle(registrationService);

        // tenant service
        KapuaTenantService tenantService = new KapuaTenantService();
        vertx.deployVerticle(tenantService);

        //credentials service
        KapuaCredentialsService credentialsService = new KapuaCredentialsService();
        vertx.deployVerticle(credentialsService);
    }
}
