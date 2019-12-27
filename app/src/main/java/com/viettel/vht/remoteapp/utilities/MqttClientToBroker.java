package com.viettel.vht.remoteapp.utilities;

import android.content.Context;
import android.util.Log;


import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.viettel.vht.remoteapp.common.AirPurifierTopics;
import com.viettel.vht.remoteapp.common.Constants;
import com.viettel.vht.remoteapp.common.ControlMode;
import com.viettel.vht.remoteapp.common.DevicesTopics;
import com.viettel.vht.remoteapp.common.MitsubishiFanTopics;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.UUID;

public class MqttClientToBroker implements Serializable, MqttCallback {
    private final String LOG_TAG = MqttClientToBroker.class.getCanonicalName();

    // --- Constants to modify per your configuration ---

    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a3oosh7oql9nlc-ats.iot.us-east-1.amazonaws.com";
    // Name of the AWS IoT policy to attach to a newly created certificate
    private static final String AWS_IOT_POLICY_NAME = "broadlink-Policy";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_EAST_1;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "iot_keystore";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "password";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "default";

    // Mqtt client
    private AWSIotClient mIotAndroidClient;

    private AWSIotMqttManager mqttManager = null;

    private boolean isConnected = false;

    public boolean isConnecting() {
        return isConnecting;
    }

    public void setConnecting(boolean connecting) {
        isConnecting = connecting;
    }

    private boolean isConnecting = false;

    private String clientId;

    private String keystorePath;
    private String keystoreName;
    private String keystorePassword;

    public KeyStore getClientKeyStore() {
        return clientKeyStore;
    }

    public void setClientKeyStore(KeyStore clientKeyStore) {
        this.clientKeyStore = clientKeyStore;
    }

    private KeyStore clientKeyStore = null;
    private String certificateId;

    // Parameter for mqtt manager
    private final int KEEP_ALIVE_TIME = 10;
    private final int MAX_AUTO_CONNECT_ATTEMPTS = 2;


    public MqttClientToBroker(Context context) {
        clientId = UUID.randomUUID().toString();
        // Init value
        // TODO process if duplicate clientId
        setConnecting(true);
        connectToServer(context);
    }

    private void initIotClient(Context context) {
        // Get region
        Region region = Region.getRegion(MY_REGION);

        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);
        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(KEEP_ALIVE_TIME);
        // Set maximum auto reconnect
        mqttManager.setMaxAutoReconnectAttempts(MAX_AUTO_CONNECT_ATTEMPTS);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament(MitsubishiFanTopics.LOST_CONNECTION.getvalue(), "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(AWSMobileClient.getInstance());
        mIotAndroidClient.setRegion(region);

//        keystorePath = getFilesDir().getPath();
        keystorePath = context.getFilesDir().getPath();
        keystoreName = KEYSTORE_NAME;
        keystorePassword = KEYSTORE_PASSWORD;
        certificateId = CERTIFICATE_ID;

        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword)) {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);

                } else {
                    Log.i(LOG_TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(LOG_TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
        }


        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Create a new private key and certificate. This call
                        // creates both on the server and returns them to the
                        // device.
                        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                                new CreateKeysAndCertificateRequest();
                        createKeysAndCertificateRequest.setSetAsActive(true);
                        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                        createKeysAndCertificateResult =
                                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                        Log.i(LOG_TAG,
                                "Cert ID: " +
                                        createKeysAndCertificateResult.getCertificateId() +
                                        " created.");

                        // store in keystore for use in MQTT client
                        // saved as alias "default" so a new certificate isn't
                        // generated each run of this application
                        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                                createKeysAndCertificateResult.getCertificatePem(),
                                createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                                keystorePath, keystoreName, keystorePassword);

                        // load keystore from file into memory to pass on
                        // connection
                        clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                                keystorePath, keystoreName, keystorePassword);

                        // Attach a policy to the newly created certificate.
                        // This flow assumes the policy was already created in
                        // AWS IoT and we are now just attaching it to the
                        // certificate.
                        AttachPrincipalPolicyRequest policyAttachRequest =
                                new AttachPrincipalPolicyRequest();
                        policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                        policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                                .getCertificateArn());
                        mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Exception occurred when generating new private key and certificate.", e);
                    }
                }
            }).start();
        }
        // Make a new connection to server
        makeConnectionToServer();
    }

    public void makeConnectionToServer() {
        // Connect to aws server or mqtt cloud
        Log.d(LOG_TAG, "clientId = " + clientId);
        try {
            if (mqttManager != null) {
                // Connect in AWS
                mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback() {
                    @Override
                    public void onStatusChanged(AWSIotMqttClientStatus status, final Throwable throwable) {
                        Log.d(LOG_TAG, "Status = " + status.name());
                        // Change connected status in remote control
                        if (status.name().equals(AWSIotMqttClientStatus.Connected.name())) {
                            // Run in ui thread
                            setConnected(true);
                            setConnecting(false);
                        } else if (status.name().equals(AWSIotMqttClientStatus.ConnectionLost.name())) {
                            setConnected(false);
                            setConnecting(false);
                        } else if (status.name().equals(AWSIotMqttClientStatus.Connecting.name())
                                || status.name().equals(AWSIotMqttClientStatus.Reconnecting.name())) {
                            setConnected(false);
                            setConnecting(true);
                        }

                    }
                });
            } else {
                // Connect in mqtt cloud
                connectToMqttCloud();
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Connection error. ", e);
        }
    }

    public void connectToServer(final Context context) {
        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        // Connect to aws iot server
        AWSMobileClient.getInstance().initialize(context, new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails result) {
                initIotClient(context);
            }

            @Override
            public void onError(Exception e) {
                Log.e(LOG_TAG, "onError: ", e);
            }
        });
    }



    public void requestDeviceInfos() {
        String msg = "getinfo";
        this.publish(msg, DevicesTopics.REQUEST_DEVICE_INFO);
    }

    public void requestAllStatesOfDevice(String smartPlugId) {
        // request power of device
        requestPowerStateOfDevice(smartPlugId);
        // Check speed of device
        requestSpeedStateOfDevice(smartPlugId);
        // request remote control mode
        requestGetCurrentControlMode();
    }

    /**
     * request power state of device
     * @param smartPlugId
     */
    public void requestPowerStateOfDevice(String smartPlugId) {
        publish("checkpower-" + smartPlugId, AirPurifierTopics.REQUEST_STATE_POWER);
    }

    /**
     *  request speed state of device
     * @param smartPlugId
     * @throws InterruptedException
     */
    public void requestSpeedStateOfDevice(String smartPlugId) {
        publish("checkspeed-" + smartPlugId, AirPurifierTopics.REQUEST_STATE_SPEED);
    }


    private void disconnect() {
        try {
            mqttManager.disconnect();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Disconnect error.", e);
        }
    }

//    /* Publish for AWS */
//    public boolean publish(String msg, String topic) {
//        Log.d(LOG_TAG, "publish message: " + msg + ", to topic: " + topic);
//        // Check connected
//        if (isConnected) {
//            mqttManager.publishString(msg, topic, AWSIotMqttQos.QOS0);
//            return true;
//        }
//        return false;
//    }

    public boolean subscribe(String topic, AWSIotMqttNewMessageCallback callback) {
        Log.d(LOG_TAG, "subscribe topic = " + topic);
        if (isConnected) {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS1, callback);
            return true;
        }
        return false;
    }


    public void changePowerOn(String deviceId) {
        publish("play-" + deviceId, AirPurifierTopics.POWER_ON);
    }

    public void changePowerOff(String deviceId) {
        publish("play-" + deviceId, AirPurifierTopics.POWER_OFF);
    }

    public void changeSpeedToLow(String deviceId) {
        publish("play-" + deviceId, AirPurifierTopics.LOW_SPEED);
    }

    public void changeSpeedToMed(String deviceId) {
        publish("play-" + deviceId, AirPurifierTopics.MED_SPEED);
    }

    public void changeSpeedToHigh(String deviceId) {
        publish("play-" + deviceId, AirPurifierTopics.HIGH_SPEED);
    }

    public void changeSmartPlugPower(String smartPlugId) {
        publish("setpower-"+smartPlugId, DevicesTopics.REQUEST_DEVICE_INFO);
    }

    /**
     * Get current mode
     */
    public void requestGetCurrentControlMode() {
        Log.d(LOG_TAG, "Get current control mode");
        publish("getairthinxmode", DevicesTopics.REQUEST_GET_CURRENT_MODE_TOPIC);
    }

    /**
     * Set control mode
     * @param mode
     */
    public void changeControlMode(ControlMode mode) {
        Log.d(LOG_TAG, "Set current control mode");
        publish("setairthinxmode-" + mode.getValue(), DevicesTopics.REQUEST_SET_CURRENT_MODE_TOPIC);
    }


    /* New Mqtt Client */
    private final int qos = 1;
    private MqttClient client;
//    private boolean isConnected = false;
    private MqttConnectOptions conOpt;
//    String clientId = null;
    private String host;

    public MqttClientToBroker(String uri) throws MqttException, URISyntaxException {
        this(new URI(uri));
    }

    public MqttClientToBroker(URI uri) throws MqttException {
        Log.i(LOG_TAG, "Start connect to mqtt server in Mqtt Cloud");
        host = String.format("tcp://%s:%d", uri.getHost(), uri.getPort());
        String[] auth = this.getAuth(uri);
        String username = auth[0];
        String password = auth[1];
        clientId = UUID.randomUUID().toString();

        conOpt = new MqttConnectOptions();
        conOpt.setCleanSession(true);
        conOpt.setUserName(username);
        conOpt.setPassword(password.toCharArray());

        // Connect to server
        // Set null for aws
        mqttManager = null;
        // Start connectToMqttCloud mqtt cloud
        makeConnectionToServer();
    }

    public boolean connectToMqttCloud()  {
        Log.i(LOG_TAG, "Connect to server:" + host + "\nClientId: " + clientId + "\nUsername: " + conOpt.getUserName());
        try {
            this.client = new MqttClient(host, clientId, new MemoryPersistence());
            this.client.setCallback(this);
            this.client.connect(conOpt);

            // Mark connected
            setConnected(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }


    private String[] getAuth(URI uri) {
        String a = uri.getAuthority();
        String[] first = a.split("@");
        return first[0].split(":");
    }

    /**
     * @see MqttCallback#connectionLost(Throwable)
     */
    public void connectionLost(Throwable cause) {
        System.out.println("Connection lost because: " + cause);
        // Try to reconnect
        try {
            for (int i = 0; i < MAX_AUTO_CONNECT_ATTEMPTS; i++) {
                if (!this.client.isConnected()) {
                    this.connectToMqttCloud();
                } else {
                    break;
                }

                Thread.sleep(Constants.WAIT_TO_STATE_CHANGE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        setConnected(false);
    }

    /**
     * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
     */
    public void deliveryComplete(IMqttDeliveryToken token) {
        try {
            Log.i(LOG_TAG, "Delivery complete with message: " + new String(token.getTopics()[0]));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * @see MqttCallback#messageArrived(String, MqttMessage)
     */
    public void messageArrived(String topic, MqttMessage message) throws MqttException {
        // Log
        Log.i(LOG_TAG, "[" + topic + "]" + " " + new String(message.getPayload()));
    }

    /**
     * Subscribe a topic
     * @param topic
     * @param listener
     * @return
     */
    public boolean subscribe(String topic, IMqttMessageListener listener) {
        Log.i(LOG_TAG, "subscribe topic: " + topic);
        try {
            // Subscribe the topic
            this.client.subscribe(topic, listener);
        } catch (MqttException me) {
            // Error
            Log.e(LOG_TAG, me.getMessage());
            me.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Publish a message to server
     * @param topic
     * @param payload
     * @return
     */
    private boolean publish(String payload, String topic) {
        Log.i(LOG_TAG, "Publish to topic = " + topic + ", payload = " + payload);
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(qos);
            // Publish to server
            this.client.publish(topic, message); // Blocking publish
        } catch (MqttException me) {
            // Error
            Log.e(LOG_TAG, me.getMessage());
            me.printStackTrace();
            return false;
        }

        return true;
    }




    // Getter and setter
    public String getClientId() {
        return clientId;
    }

    public AWSIotMqttManager getMqttManager() {
        return mqttManager;
    }


    public boolean isConnected() {
        if (client != null) {
            isConnected = client.isConnected();
        }
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }


}
