package com.redhat.iot;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.broker.BrokerDomains;
import org.eclipse.kapua.broker.core.BrokerJAXBContextProvider;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.commons.util.xml.JAXBContextProvider;
import org.eclipse.kapua.commons.util.xml.XmlUtil;
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
import org.eclipse.kapua.service.device.management.DeviceManagementDomains;
import org.eclipse.kapua.service.device.registry.*;
import org.eclipse.kapua.service.device.steps.PermissionData;
import org.eclipse.kapua.service.liquibase.KapuaLiquibaseClient;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserCreator;
import org.eclipse.kapua.service.user.UserService;
import org.eclipse.kapua.service.user.internal.UserFactoryImpl;

import java.util.*;

public class SimpleKapuaInstance {

    public static AccountService accountService;
    public static UserService userService;
    public static CredentialService credentialService;
    public static DeviceRegistryService deviceRegistryService;
    public static AccessInfoService accessInfoService;

    private static DBHelper dbHelper = new DBHelper();

    public static void start() throws KapuaException {


        System.setProperty(SystemSettingKey.DB_SCHEMA.key(), "kapuadb");
        //new KapuaLiquibaseClient("jdbc:h2:mem:kapua;MODE=MySQL", "sa", "").update();

        new KapuaLiquibaseClient("jdbc:h2:tcp://localhost:3306/kapuadb;MODE=MySQL",
                "kapua", "kapua", Optional.of("kapuadb"));


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

            /* now create the credentials for :
            *  device 4711 :
            *       - sensor1
            *       - sensor2
            *       - little-sensor2
            *  device gw-1
            *       - gateway
            */

            // Create user sensor1
            UserCreator sensor1Creator = new UserFactoryImpl().newCreator(account.getId(),"sensor1");
            Properties userProps = new Properties();
            userProps.setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "4711");
            sensor1Creator.setEntityAttributes(userProps);
            User sensor1 = userService.create(sensor1Creator);

            // Create credentials for sensor1
            CredentialCreator credentialCreator;
            credentialCreator = new CredentialFactoryImpl().newCreator(
                    account.getId(),
                    sensor1.getId(),
                    CredentialType.PASSWORD,
                    "hono-secret",
                    CredentialStatus.ENABLED,
                    //new Date("2037-06-01T14:00:00+01:00")
                    null);
            credentialService.create(credentialCreator);

            /* Error: Credential of type PASSWORD already exists for this user.
            credentialCreator = new CredentialFactoryImpl().newCreator(
                    account.getId(),
                    sensor1.getId(),
                    CredentialType.PASSWORD,
                    "hono-secret",
                    CredentialStatus.ENABLED,
                    //new Date("2037-05-01T14:00:00+01:00")
                    null);
            credentialService.create(credentialCreator);
            */


            // Create user sensor2
            UserCreator sensor2Creator = new UserFactoryImpl().newCreator(account.getId(),"sensor2");
            userProps = new Properties();
            userProps.setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "4711");
            sensor2Creator.setEntityAttributes(userProps);
            User sensor2 = userService.create(sensor2Creator);
            // create credentials
            credentialCreator = new CredentialFactoryImpl().newCreator(
                    account.getId(),
                    sensor2.getId(),
                    CredentialType.PASSWORD,
                    "hono-secret",
                    CredentialStatus.ENABLED,
                    //new Date("2037-06-01T14:00:00+01:00")
                    null);
            credentialService.create(credentialCreator);

            // Create user little-sensor2
            UserCreator littleSensor2Creator = new UserFactoryImpl().newCreator(account.getId(),"little-sensor2");
            userProps = new Properties();
            userProps.setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "4711");
            littleSensor2Creator.setEntityAttributes(userProps);
            User littleSensor2 = userService.create(littleSensor2Creator);
            // create credentials
            credentialCreator = new CredentialFactoryImpl().newCreator(
                    account.getId(),
                    littleSensor2.getId(),
                    CredentialType.PASSWORD,
                    "c2VjcmV0S2V5",
                    CredentialStatus.ENABLED,
                    //new Date("2037-06-01T14:00:00+01:00")
                    null);
            credentialService.create(credentialCreator);

            /* Error: Credential of type PASSWORD already exists for this user.
            credentialCreator = new CredentialFactoryImpl().newCreator(
                    account.getId(),
                    littleSensor2.getId(),
                    CredentialType.PASSWORD,
                    "c2VjcmV0S2V5Mg==",
                    CredentialStatus.ENABLED,
                    //new Date("2037-05-01T14:00:00+01:00")
                    null);
            credentialService.create(credentialCreator);
            */

            // Create user gw
            UserCreator gwUserCreator = new UserFactoryImpl().newCreator(account.getId(),"gateway");
            userProps = new Properties();
            userProps.setProperty(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "gw-1");
            userProps.setProperty("client-id", "gateway-one");
            gwUserCreator.setEntityAttributes(userProps);
            User gwUser = userService.create(gwUserCreator);
            // create credentials
            credentialCreator = new CredentialFactoryImpl().newCreator(
                    account.getId(),
                    gwUser.getId(),
                    CredentialType.PASSWORD,
                    "gw-secret",
                    CredentialStatus.ENABLED,
                    //new Date("2037-06-01T14:00:00+01:00")
                    null);
            credentialService.create(credentialCreator);


            // Permissions
            List<PermissionData> permissionList = new ArrayList<>();
            permissionList.add(new PermissionData(BrokerDomains.BROKER_DOMAIN, Actions.connect, (KapuaEid) sensor1.getScopeId()));
            permissionList.add(new PermissionData(BrokerDomains.BROKER_DOMAIN, Actions.write, (KapuaEid) sensor1.getScopeId()));
            permissionList.add(new PermissionData(BrokerDomains.BROKER_DOMAIN, Actions.read, (KapuaEid) sensor1.getScopeId()));
            permissionList.add(new PermissionData(BrokerDomains.BROKER_DOMAIN, Actions.delete, (KapuaEid) sensor1.getScopeId()));

            permissionList.add(new PermissionData(DeviceManagementDomains.DEVICE_MANAGEMENT_DOMAIN, Actions.write, (KapuaEid) sensor1.getScopeId()));

            PermissionFactory permissionFactory = new PermissionFactoryImpl();
            AccessInfoCreator accessInfoCreator = new AccessInfoFactoryImpl().newCreator(account.getId());
            accessInfoCreator.setUserId(sensor1.getId());
            accessInfoCreator.setScopeId(sensor1.getScopeId());
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


            // Create devices

            DeviceFactory deviceFactory = locator.getFactory(DeviceFactory.class);
            DeviceCreator deviceCreator = deviceFactory.newCreator(account.getScopeId(), "4711");
            deviceCreator.setDisplayName("Test Device");
            Device device = deviceRegistryService.create(deviceCreator);

            deviceCreator = deviceFactory.newCreator(account.getScopeId(), "disabled-device");
            deviceCreator.setStatus(DeviceStatus.DISABLED);
            deviceRegistryService.create(deviceCreator);

        });
    }
}
