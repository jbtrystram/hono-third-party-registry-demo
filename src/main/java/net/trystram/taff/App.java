package net.trystram.taff;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
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
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.broker.BrokerService;
import org.eclipse.kapua.broker.core.BrokerJAXBContextProvider;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.util.xml.JAXBContextProvider;
import org.eclipse.kapua.commons.util.xml.XmlUtil;
import org.eclipse.kapua.hono.KapuaCredentialsService;
import org.eclipse.kapua.hono.KapuaRegistrationService;
import org.eclipse.kapua.hono.KapuaTenantService;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.model.domain.Domain;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.qa.steps.DBHelper;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountCreator;
import org.eclipse.kapua.service.account.AccountFactory;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.authentication.credential.CredentialCreator;
import org.eclipse.kapua.service.authentication.credential.CredentialService;
import org.eclipse.kapua.service.authentication.credential.CredentialStatus;
import org.eclipse.kapua.service.authentication.credential.CredentialType;
import org.eclipse.kapua.service.authentication.credential.shiro.CredentialFactoryImpl;
import org.eclipse.kapua.service.authorization.access.AccessInfoCreator;
import org.eclipse.kapua.service.authorization.access.AccessInfoService;
import org.eclipse.kapua.service.authorization.access.shiro.AccessInfoFactoryImpl;
import org.eclipse.kapua.service.authorization.permission.Permission;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.authorization.permission.shiro.PermissionFactoryImpl;
import org.eclipse.kapua.service.device.management.DeviceManagementService;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceCreator;
import org.eclipse.kapua.service.device.registry.DeviceFactory;
import org.eclipse.kapua.service.device.registry.DeviceRegistryService;
import org.eclipse.kapua.service.device.steps.PermissionData;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserCreator;
import org.eclipse.kapua.service.user.UserService;
import org.eclipse.kapua.service.user.internal.UserFactoryImpl;
import org.springframework.core.io.FileSystemResource;

import java.util.*;

public class App {


    // kapua stuff
    public static AccountService accountService;
    public static UserService userService;
    public static CredentialService credentialService;
    public static DeviceRegistryService deviceRegistryService;
    public static AccessInfoService accessInfoService;

    private static DBHelper dbHelper = new DBHelper();

    private static void setupKapua() throws KapuaException {

        dbHelper.setup();

        KapuaSecurityUtils.doPrivileged(() -> {
            KapuaLocator locator = KapuaLocator.getInstance();

            JAXBContextProvider brokerProvider = new BrokerJAXBContextProvider();
            XmlUtil.setContextProvider(brokerProvider);
            userService = locator.getService(UserService.class);

            Map<String, Object> valueMap = new HashMap();
            valueMap.put("infiniteChildEntities", true);
            valueMap.put("maxNumberChildEntities", 5);
            valueMap.put("lockoutPolicy.enabled", false);
            valueMap.put("lockoutPolicy.maxFailures", 3);
            valueMap.put("lockoutPolicy.resetAfter", 300);
            valueMap.put("lockoutPolicy.lockDuration", 3);

            accountService = locator.getService(AccountService.class);
            deviceRegistryService = locator.getService(DeviceRegistryService.class);
            credentialService = locator.getService(CredentialService.class);
            deviceRegistryService = locator.getService(DeviceRegistryService.class);
            accessInfoService = locator.getService(AccessInfoService.class);

            // Create DEFAULT_TENANT account
            AccountFactory accountFactory = locator.getFactory(AccountFactory.class);
            AccountCreator accountCreator = accountFactory.newCreator(KapuaId.ONE, "DEFAULT_TENANT");
            accountCreator.setOrganizationName("Test Tenant");
            accountCreator.setOrganizationEmail("tenant@example.com");
            Properties tenantProps = new Properties();
            tenantProps.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonObject()
                    .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=ca,OU=Hono,O=Eclipse")
                    .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY")
                    .encode());
            accountCreator.setEntityAttributes(tenantProps);
            Account account = accountService.create(accountCreator);

            // Create HTTP_ONLY account
            accountCreator = accountFactory.newCreator(KapuaId.ONE, "HTTP_ONLY");
            accountCreator.setOrganizationName("Test Tenant");
            accountCreator.setOrganizationEmail("tenant@example.com");
            tenantProps = new Properties();
            tenantProps.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonObject()
                    .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=ca-http,OU=Hono,O=Eclipse")
                    .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAPUBLICKEY")
                    .encode());
            tenantProps.setProperty(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(
                    new JsonObject().put(TenantConstants.FIELD_ADAPTERS_TYPE, "hono-http")
                    .put(TenantConstants.FIELD_ENABLED, true)
                    .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true))
                    .encode());
            accountCreator.setEntityAttributes(tenantProps);
            Account account2 = accountService.create(accountCreator);


            userService.setConfigValues(account.getId(), account.getScopeId(), valueMap);
            userService.setConfigValues(account2.getId(), account2.getScopeId(), valueMap);

            // Create user gateway
            Properties userprops = new Properties();
            userprops.setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "gw-1");
            userprops.setProperty(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
            userprops.setProperty(CredentialsConstants.FIELD_AUTH_ID, "gateway");
            userprops.setProperty("client-id", "gateway-one");
            userprops.setProperty(CredentialsConstants.FIELD_ENABLED, "true");
            userprops.setProperty(CredentialsConstants.FIELD_SECRETS,
                    new JsonArray().add(new JsonObject()
                    .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2018-01-01T00:00:00+01:00")
                    .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00")
                    .put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, "sha-512")
                    .put(CredentialsConstants.FIELD_SECRETS_SALT, "Z3dzYWx0")
                    .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "CrHirmXdU20qORDRE5gbphS9vUCdl2NhEaAikw6EGTwBevbs1rCwml82cONPNxugZ1D0QHQSnbY4gWJQJi6P4g=="))
                    .encode());

            UserCreator userCreator = new UserFactoryImpl().newCreator(account.getId(), userprops.getProperty(CredentialsConstants.FIELD_AUTH_ID));
            userCreator.setEntityAttributes(userprops);
            User user = userService.create(userCreator);

            // Create user sensor1
            userprops = new Properties();
            userprops.setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "4711");
            userprops.setProperty(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
            userprops.setProperty(CredentialsConstants.FIELD_AUTH_ID, "sensor1");
            userprops.setProperty(CredentialsConstants.FIELD_ENABLED, "true");
            userprops.setProperty(CredentialsConstants.FIELD_SECRETS,
                    new JsonArray().add(new JsonObject()
                                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00")
                                .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00")
                                .put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, "sha-512")
                                .put(CredentialsConstants.FIELD_SECRETS_SALT, "aG9ubw==")
                                .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA===="))
                            .add(new JsonObject()
                                 .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2017-05-15T14:00:00+01:00")
                                 .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2037-05-01T14:00:00+01:00")
                                 .put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, "sha-512")
                                 .put(CredentialsConstants.FIELD_SECRETS_SALT, "aG9ubzI=")
                                 .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "QDhkSQcm0HNBybnuc5irvPIgNUJn0iVoQnFSoltLOsDlfxhcQWa99l8Dhh67jSKBr7fXeSvFZ1mEojReAXz18A=="))
                            .encode());

            userCreator = new UserFactoryImpl().newCreator(account.getId(), userprops.getProperty(CredentialsConstants.FIELD_AUTH_ID));
            userCreator.setEntityAttributes(userprops);
            User user2 = userService.create(userCreator);

            // Create credentials
            CredentialCreator credentialCreator;
            credentialCreator = new CredentialFactoryImpl().newCreator(account.getId(), user.getId(), CredentialType.PASSWORD, "gw-secret", CredentialStatus.ENABLED, null);
            credentialService.create(credentialCreator);

            credentialCreator = new CredentialFactoryImpl().newCreator(account.getId(), user2.getId(), CredentialType.PASSWORD, "hono-secret", CredentialStatus.ENABLED, null);
            credentialService.create(credentialCreator);

            // Permissions
            List<PermissionData> permissionList = new ArrayList<>();
            permissionList.add(new PermissionData(BrokerService.BROKER_DOMAIN, Actions.connect, (KapuaEid) user.getScopeId()));
            permissionList.add(new PermissionData(BrokerService.BROKER_DOMAIN, Actions.write, (KapuaEid) user.getScopeId()));
            permissionList.add(new PermissionData(BrokerService.BROKER_DOMAIN, Actions.read, (KapuaEid) user.getScopeId()));
            permissionList.add(new PermissionData(BrokerService.BROKER_DOMAIN, Actions.delete, (KapuaEid) user.getScopeId()));

            permissionList.add(new PermissionData(DeviceManagementService.DEVICE_MANAGEMENT_DOMAIN, Actions.write, (KapuaEid) user.getScopeId()));

            PermissionFactory permissionFactory = new PermissionFactoryImpl();
            AccessInfoCreator accessInfoCreator = new AccessInfoFactoryImpl().newCreator(account.getId());
            accessInfoCreator.setUserId(user.getId());
            accessInfoCreator.setScopeId(user.getScopeId());
            Set<Permission> permissions = new HashSet<>();
            for (PermissionData permissionData : permissionList) {
                Actions action = permissionData.getAction();
                KapuaEid targetScopeId = permissionData.getTargetScopeId();
                if (targetScopeId == null) {
                    targetScopeId = (KapuaEid) account.getId();
                }
                Domain domain = permissionData.getDomain();
                Permission permission = permissionFactory.newPermission(domain, action, targetScopeId);
                permissions.add(permission);
            }
            accessInfoCreator.setPermissions(permissions);

            accessInfoService.create(accessInfoCreator);

            // Create device

            DeviceFactory deviceFactory = locator.getFactory(DeviceFactory.class);
            DeviceCreator deviceCreator = deviceFactory.newCreator(account.getScopeId(), "test-device");
            deviceCreator.setDisplayName("Test Device");
            Device device = deviceRegistryService.create(deviceCreator);

        });

    }

    public static void main(String[] args) throws Exception {

        setupKapua();

        Vertx vertx = Vertx.vertx();

        // AMQP server properties
        ServiceConfigProperties registrationProps = new ServiceConfigProperties();
        registrationProps.setInsecurePort(25672);
        //registrationProps.setPort(25672);
        registrationProps.setBindAddress("0.0.0.0");
        registrationProps.setKeyStorePath("src/test/resources/certificates/deviceRegistryKeyStore.p12");
        registrationProps.setKeyStorePassword("deviceregistrykeys");

        DeviceRegistryAmqpServer server = new DeviceRegistryAmqpServer();
        server.setConfig(registrationProps);

        // Setup endpoints
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


        AuthenticationServerConfigProperties authProps = new AuthenticationServerConfigProperties();
        authProps.setPermissionsPath(new FileSystemResource("src/main/resources/permissions.json"));
        AuthenticationService auth = new FileBasedAuthenticationService();

        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(createAuthenticationService(createUser())));

        server.setAuthorizationService(new ClaimsBasedAuthorizationService());

        vertx.deployVerticle(server);

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
