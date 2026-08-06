import ballerina/mqtt;
import ballerinax/rabbitmq;
// import ballerinax/salesforce; // Disabled: salesforce fails to load from its BIR under the test-provisioned distribution. See https://github.com/wso2/product-integrator/issues/1920
import ballerinax/sap.jco;
import ballerinax/solace;
import ballerinax/solace.jms;

listener mqtt:Listener mqttListener = new (mqtt:DEFAULT_URL, "unique_client_001", "topic1");
listener rabbitmq:Listener orderListener = new (rabbitmq:DEFAULT_HOST, 5671);
listener rabbitmq:Listener deliveryListener = new (rabbitmq:DEFAULT_HOST, 5671);
listener solace:Listener solaceListener = new ("smf://localhost:55554", messageVpn = "default");
// listener salesforce:Listener salesforceListener = new ({auth: {username: "abcd", password: "xxxx"}});
listener jco:Listener sapJcoListener = new ({gwhost: "sap-gw.example.com", gwserv: "3300", progid: "JCO_LISTENER", repositoryDestination: "MY_SAP_DEST"});
listener jms:Listener solaceJmsListener = new ("smf://localhost:55555", messageVpn = "default");
