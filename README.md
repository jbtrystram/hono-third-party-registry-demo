# hono-third-party-registry-demo

This repo contain early work trying to build a simple java application implementing a device registry complying to the Eclipse Hono Device Registry API.

The aim is to use Eclipse Kapua to provide the back end services, and simply write a proxy application. 

To be able to run this application, you need to build and install the [hono integration](https://github.com/jbtrystram/kapua/tree/feature-eclipseiot/extras/hono-integration) module of kapua.
This can be easily done cloning the repo and running `mvn install`

Then simply run this application in an ide or with `mvn compile && mvn exec:java`

We aim to build a device registry that pass the Device Registry tests provided by the Hono project. [Tests source](https://github.com/eclipse/hono/tree/master/tests/src/test/java/org/eclipse/hono/tests/registry).
Run the tests from your IDE or with `mvn verify -Dtest=TenantAmqpIT`


## What works

* AMQP tests for the Tenant API.


## What left

* Finishing the credential API
* Expose HTTP endpoints
* Device Registration API