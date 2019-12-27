package com.viettel.vht.remoteapp.ui.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.amazonaws.mobile.auth.core.internal.util.ThreadUtils;
import com.viettel.vht.remoteapp.MainActivity;
import com.viettel.vht.remoteapp.R;
import com.viettel.vht.remoteapp.common.Constants;
import com.viettel.vht.remoteapp.common.ControlMode;
import com.viettel.vht.remoteapp.common.PowerState;
import com.viettel.vht.remoteapp.common.SpeedState;
import com.viettel.vht.remoteapp.monitoring.MonitoringSystem;
import com.viettel.vht.remoteapp.objects.AirPurifier;
import com.viettel.vht.remoteapp.utilities.MqttClientToAWS;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;
    GridView gdView1, gdView2, gdView3;
    RelativeLayout aqStatus;
    TextView txtAQValue, txtAQLevel, txtAQTitle;
    ProgressBar loadingBar;
    ImageView dsIcon;
    TextView dsText;

    private Thread monitoringThread;
    private MonitoringSystem monitoringSystem;
    private MainActivity parentActivity;
    private MqttClientToAWS mqttClient;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SwipeRefreshLayout.OnRefreshListener refreshListener;

    // Sound button
    private MediaPlayer soundButton;
    // Vibrator when click button
    private Vibrator vibrator;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        monitoringSystem = new MonitoringSystem(getActivity());
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = ViewModelProviders.of(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        // define views
        gdView1 = (GridView) root.findViewById(R.id.gdHTP);
        gdView2 = (GridView) root.findViewById(R.id.gdCO2);
        gdView3 = (GridView) root.findViewById(R.id.gdPM);
        aqStatus = (RelativeLayout) root.findViewById(R.id.aq_status);
        txtAQValue = (TextView) root.findViewById(R.id.aq_status_value);
        txtAQLevel = (TextView) root.findViewById(R.id.aq_status_level);
        txtAQTitle = (TextView) root.findViewById(R.id.aq_status_title);
        loadingBar = (ProgressBar) root.findViewById(R.id.loading);
        dsIcon = (ImageView) root.findViewById(R.id.ds_icon);
        dsText = (TextView) root.findViewById(R.id.ds_text);

        // getting data in another thread
        monitoringThread = new Thread() {
            @Override
            public void run() {
                loadingStatusData();
            }
        };
        monitoringThread.start();

        // Hungdv39 change below
        // Power
        mBtPower = root.findViewById(R.id.bt_power);
        mBtPower.setOnClickListener(btPowerClick);
        // Low speed
        mBtLowSpeed = root.findViewById(R.id.bt_low_speed);
        mBtLowSpeed.setOnClickListener(btSpeedClick);
        // Medium speed
        mBtMedSpeed = root.findViewById(R.id.bt_med_speed);
        mBtMedSpeed.setOnClickListener(btSpeedClick);
        // High speed
        mBtHighSpeed = root.findViewById(R.id.bt_high_speed);
        mBtHighSpeed.setOnClickListener(btSpeedClick);

        // Switch
        mSwitchMode = root.findViewById(R.id.sw_mode);
        mSwitchMode.setOnClickListener(switchModeListener);
        // parent activity
        parentActivity = (MainActivity) getActivity();

        // Dialog
        mCannotRemoteDevice = new AlertDialog.Builder(parentActivity)
                .setTitle(R.string.info)
                .setMessage(R.string.cannot_remote_device)
                .setNegativeButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i(LOG_TAG, "OK button click on CannotRemoteDevice dialog");
                    }
                }).create();


        // Get mqtt client
        mqttClient = parentActivity.getMqttClient();
        // get expected state in device

        // determine how many device remote
        expectedStateInDevice = parentActivity.getRealState();
        stateInUI = new AirPurifier();

        // Get swipe refresh
        swipeRefreshLayout = parentActivity.findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setEnabled(true);
        // Get listener
        refreshListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.i(LOG_TAG, "Start swipe refresh layout");
                startRefresh();
            }
        };

        swipeRefreshLayout.setOnRefreshListener(refreshListener);

        // Set refreshing
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
            }
        });


        // Get sound button
        soundButton = MediaPlayer.create(parentActivity, R.raw.sample_3);
        // Get vibrate
        vibrator = (Vibrator) parentActivity.getSystemService(Context.VIBRATOR_SERVICE);

        // Wait to update ui
        disableAllButton();
        updateUI();

        return root;
    }

    private void loadingStatusData(){
        while (true) {
            try {
                if (getActivity() == null){
                    System.out.println("EXCEPCTION: Activity null...");
                    return;
                }
                System.out.println("Getting data...");
                monitoringSystem.readAndDisplayStatus(aqStatus, txtAQValue, txtAQTitle, txtAQLevel, gdView1, gdView2, gdView3, loadingBar, dsIcon, dsText);
                Thread.sleep(Constants.UPDATE_DATA_TIME * 1000); // get data for each 5s

            } catch (InterruptedException e) { // stop getting data
                System.out.println("Stop getting data....");
                break;
            }
        }
    }

    @Override
    public void onResume() {
        Log.i(LOG_TAG, "On resume");
        if (!monitoringThread.isAlive()){
            System.out.println("RESTARTING data...");
            monitoringThread = new Thread() {
                @Override
                public void run() {
                    loadingStatusData();
                }
            };
            monitoringThread.start();
        }
        System.out.println("STARTING data...");
        super.onResume();

        // [Hungdv39] resume update ui
        updateUI();
    }

    @Override
    public void onStop() {
        Log.i(LOG_TAG, "On stop");
        super.onStop();
        monitoringThread.interrupt(); // stop getting data

        // [Hungdv39] Stop update ui
        loopFlag = false;
    }

    // Hungdv39 add variable
    private Button mBtPower, mBtLowSpeed, mBtMedSpeed, mBtHighSpeed;
    private AirPurifier expectedStateInDevice;
    private Switch mSwitchMode;
    private AirPurifier stateInUI;

    static final String LOG_TAG = HomeFragment.class.getCanonicalName();

    View.OnClickListener btPowerClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(LOG_TAG, "Click power button");
            // Sound and vibrator
            soundButton.start();
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
            // Start work flow
            power(v);
        }
    };

    View.OnClickListener btSpeedClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Sound and vibrator
            soundButton.start();
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
            // Workflow
            switch(v.getId()) {
                case R.id.bt_low_speed:
                    expectedStateInDevice.setSpeed(SpeedState.LOW);
                    stateInUI.setSpeed(SpeedState.LOW);
                    uiInLowSpeed();
                    Log.i(LOG_TAG, "low speed");
                    // Send message to server
                    mqttClient.changeSpeedToLow(parentActivity.getRemoteDeviceId());
                    break;
                case R.id.bt_med_speed:
                    expectedStateInDevice.setSpeed(SpeedState.MED);
                    stateInUI.setSpeed(SpeedState.MED);
                    uiInMedSpeed();
                    Log.i(LOG_TAG, "med speed");
                    // Send message to server
                    mqttClient.changeSpeedToMed(parentActivity.getRemoteDeviceId());
                    break;
                case R.id.bt_high_speed:
                    expectedStateInDevice.setSpeed(SpeedState.HIGH);
                    stateInUI.setSpeed(SpeedState.HIGH);
                    uiInHighSpeed();
                    Log.i(LOG_TAG, "high speed");
                    // Send message to server
                    mqttClient.changeSpeedToHigh(parentActivity.getRemoteDeviceId());
                    break;
                default:
                    Log.i(LOG_TAG, "wrong view : " + v.getId());
                    break;
            }
        }
    };


    View.OnClickListener switchModeListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            if (mSwitchMode.isChecked()) {
                // Auto mode
                mSwitchMode.setText(R.string.mode_auto);
                stateInUI.setControlMode(ControlMode.AUTO);
                expectedStateInDevice.setControlMode(ControlMode.AUTO);
                // Send message to server
                mqttClient.changeControlMode(ControlMode.AUTO);
                // Disable all button except switch
                parentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        disableControlButton();
                    }
                });
            } else {
                // Manual mode
                mSwitchMode.setText(R.string.mode_manual);
                stateInUI.setControlMode(ControlMode.MANUAL);
                expectedStateInDevice.setControlMode(ControlMode.MANUAL);
                // Send message to server
                mqttClient.changeControlMode(ControlMode.MANUAL);
                // Enable all button
                parentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        enableControlButton();
                    }
                });
            }


            // Get mode control
            mqttClient.requestGetCurrentControlMode();
        }
    };

    /**
     * Enable speed button
     */
    private void enableSpeedButton() {
        mBtLowSpeed.setEnabled(true);
        mBtMedSpeed.setEnabled(true);
        mBtHighSpeed.setEnabled(true);
    }

    /**
     * Disable speed button
     */
    private void disableSpeedButton() {
        mBtLowSpeed.setEnabled(false);
        mBtMedSpeed.setEnabled(false);
        mBtHighSpeed.setEnabled(false);
    }

    /**
     * Disable all button
     */
    private void disableAllButton() {
        disableControlButton();
        disableSpeedButton();
    }

    /**
     * Enable all button
     */
    private void enableAllButton() {
        enableControlButton();
        enableSpeedButton();
    }

    private void enableControlButton() {
        mBtPower.setEnabled(true);
        enableSpeedButton();
    }

    private void disableControlButton() {
        mBtPower.setEnabled(false);
        disableSpeedButton();
    }
    /**
     * ui in power on
     */
    private void uiInPowerOn() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enableSpeedButton();
                // Enable all button
                mBtPower.setEnabled(true);
                mBtPower.setBackground(getResources().getDrawable(R.drawable.bg_power_on_bt, null));
            }
        });

    }

    private void uiInRefreshData() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disableAllButton();
                uiInLoadingMode();
            }
        });
    }

    /**
     * ui in power off
     */
    private void uiInPowerOff() {
        // Disable speed button
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Disable speed button
                disableSpeedButton();
                // Enable power button
                mBtPower.setEnabled(true);
                mBtPower.setBackground(getResources().getDrawable(R.drawable.bg_power_off_bt, null));
                // Unselect all speed button
                unSelectAllSpeedButton();
            }
        });
    }

    private void unSelectAllSpeedButton() {
        mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
    }

    private void uiInLowSpeed() {
        // Disable speed button
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Enable all button
                mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_selected_speed_bt, null));
                mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
                mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
            }
        });
    }

    private void uiInMedSpeed() {
        // Disable speed button
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Enable all button
                mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
                mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_selected_speed_bt, null));
                mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
            }
        });
    }

    private void uiInHighSpeed() {
        // Disable speed button
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Enable all button
                mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
                mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
                mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_selected_speed_bt, null));
            }
        });
    }


    private void uiInAutoLowSpeed() {
        mBtPower.setBackground(getResources().getDrawable(R.drawable.bg_power_on_bt, null));
        // Low
        mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_selected_speed_bt, null));
        mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
    }

    private void uiInAutoMedSpeed() {
        mBtPower.setBackground(getResources().getDrawable(R.drawable.bg_power_on_bt, null));
        mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        // Med
        mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_selected_speed_bt, null));
        mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
    }

    private void uiInAutoHighSpeed() {
        mBtPower.setBackground(getResources().getDrawable(R.drawable.bg_power_on_bt, null));
        mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        // High
        mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_selected_speed_bt, null));
    }

    private void uiInAutoPowerOff() {
        // Off power
        mBtPower.setBackground(getResources().getDrawable(R.drawable.bg_power_off_bt, null));
        mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
        mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bg_speed_bt, null));
    }


    private void uiInLoadingMode() {
        // Loading mode
        mBtPower.setBackground(getResources().getDrawable(R.drawable.bt_grey_round, null));
        mBtLowSpeed.setBackground(getResources().getDrawable(R.drawable.bt_grey_round, null));
        mBtMedSpeed.setBackground(getResources().getDrawable(R.drawable.bt_grey_round, null));
        mBtHighSpeed.setBackground(getResources().getDrawable(R.drawable.bt_grey_round, null));
        mSwitchMode.setText(R.string.mode_loading);
    }
    /**
     * Disable all button except switch button
     */
    private void uiInAutoMode() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disableAllButton();
                mSwitchMode.setEnabled(true);
                mSwitchMode.setChecked(true);
                mSwitchMode.setText(R.string.mode_auto);
                // Check power on device
                if (expectedStateInDevice.getPower() == PowerState.OFF) {
                    uiInAutoPowerOff();
                } else {
                    switch (expectedStateInDevice.getSpeed()) {
                        case OFF:
                            uiInAutoPowerOff();
                            break;
                        case LOW:
                            uiInAutoLowSpeed();
                            break;
                        case MED:
                            uiInAutoMedSpeed();
                            break;
                        case HIGH:
                            uiInAutoHighSpeed();
                            break;
                        default:
                            Log.e(LOG_TAG, "Wrong value in speed in auto mode");
                            break;
                    }
                }
            }
        });
    }

    private void uiInManualMode() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSwitchMode.setEnabled(true);
                mSwitchMode.setChecked(false);
                mSwitchMode.setText(R.string.mode_manual);
            }
        });
    }

    private void power(View view) {
        // Check value of smart plug id
        if (stateInUI.getPower() == PowerState.OFF) {
            stateInUI.setPower(PowerState.ON);
            expectedStateInDevice.setPower(PowerState.ON);
            // Send message to the server
            mqttClient.changeSmartPlugPower(parentActivity.getSmartPlugId());
        }
        // Value of speed in smart plug id (power + speed in device)
        Log.d(LOG_TAG, "Old_state = " + stateInUI.getSpeed().name());
        if (stateInUI.getSpeed() != SpeedState.OFF) {
            // Now: power on => Set power off
            // state in ui
            Log.i(LOG_TAG, "Turn off device");
            stateInUI.setSpeed(SpeedState.OFF);
            stateInUI.setPower(PowerState.OFF);
            // expected state
            expectedStateInDevice.setSpeed(SpeedState.OFF);
            expectedStateInDevice.setPower(PowerState.OFF);
            //Set ui
            uiInPowerOff();
            // Send message to the server
            mqttClient.changePowerOff(parentActivity.getRemoteDeviceId());
        } else {
            // Now: power off => Set power on
            // State in ui
            Log.i(LOG_TAG, "Turn off device");
            stateInUI.setSpeed(SpeedState.LOW);
            stateInUI.setPower(PowerState.ON);
            // Expected state
            expectedStateInDevice.setSpeed(SpeedState.LOW);
            expectedStateInDevice.setPower(PowerState.ON);
            //Set ui
            uiInPowerOn();
            uiInLowSpeed();
            // Send message to the server
            mqttClient.changePowerOn(parentActivity.getRemoteDeviceId());
        }
    }

    private void setVisibleAllButton() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Set visible all button
                mBtPower.setVisibility(View.VISIBLE);
                mBtLowSpeed.setVisibility(View.VISIBLE);
                mBtMedSpeed.setVisibility(View.VISIBLE);
                mBtHighSpeed.setVisibility(View.VISIBLE);
                // Set visible switch
                mSwitchMode.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * check value between expected state and state in ui
     * @return
     */
    private boolean checkStateBetweenExpectedAndUI() {
        Log.d(LOG_TAG, "checkStateBetweenExpectedAndUI\nexpected state : "
                + expectedStateInDevice.toString() + "\nui state: " + stateInUI.toString());

        // Check difference
        if (expectedStateInDevice.getControlMode() != stateInUI.getControlMode() ||
            expectedStateInDevice.getPower() != stateInUI.getPower() ||
            expectedStateInDevice.getSpeed() != stateInUI.getSpeed()) {
            return true;
        }

        return false;
    }

    /**
     * Start update ui
     */
    public void updateUI() {
        // start UpdateUI
        new UpdateUI().start();
    }

    private class UpdateUI extends Thread {

        private boolean checkVisibilityUI() throws InterruptedException {
            for (int i = 0; i < Constants.MAX_CHECK_UPDATE_HOME_UI; i++) {
                // Check validate and break loop
                if (loadingBar.getVisibility() != View.VISIBLE) {
                    return true;
                }

                Thread.sleep(Constants.WAIT_TO_UPDATE_UI);
            }

            return false;
        }

        private boolean checkNullValue() throws InterruptedException {
            for (int i = 0; i < Constants.MAX_CHECK_UPDATE_HOME_UI; i++) {
                // Check validate and break loop
                if (expectedStateInDevice.isNotNull()) {
                    return true;
                }

                Thread.sleep(Constants.WAIT_TO_UPDATE_UI);
            }

            return false;
        }

        @Override
        public void run() {
            try {
                // Check visibility on ui
                if (!checkVisibilityUI()) {
                    return;
                }

                // Visiable all button
                setVisibleAllButton();

                // Wait until complete load
                if (!checkNullValue()) {
                    return;
                }

                // set ui state
                stateInUI.setPower(expectedStateInDevice.getPower());
                stateInUI.setSpeed(expectedStateInDevice.getSpeed());
                stateInUI.setControlMode(expectedStateInDevice.getControlMode());

                // Set ui
                if (expectedStateInDevice.getControlMode() == ControlMode.AUTO) {
                    // auto mode
                    uiInAutoMode();
                } else {
                    // manual mode
                    uiInManualMode();
                    // Check speed and power
                    if (expectedStateInDevice.getSpeed() == SpeedState.OFF) {
                        // off device
                        uiInPowerOff();
                    } else {
                        // on device
                        uiInPowerOn();
                        switch (expectedStateInDevice.getSpeed()) {
                            case LOW:
                                uiInLowSpeed();
                                break;
                            case MED:
                                uiInMedSpeed();
                                break;
                            case HIGH:
                                uiInHighSpeed();
                                break;
                            default:
                                Log.e(LOG_TAG, "Error in speed");
                        }
                    }
                }

                // Start check update
                if (!loopFlag) {
                    checkUpdate();
                }

            } catch (InterruptedException ie) {
                ie.printStackTrace();
            } finally {
                // stop refresh
                stopRefresh();
            }

        }
    }


    // Dialog inform to user when it cannot remote the device
    private Dialog mCannotRemoteDevice;

    /**
     * function to call cannot remote dialog
     */
    private void showCannotRemoteDeviceDialog() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCannotRemoteDevice.show();
            }
        });
    }

    private void checkUpdate() {
        loopFlag = true;
        new CheckUpdate().start();
    }

    // Flag for check update
    private boolean loopFlag = false;

    private class CheckUpdate extends Thread {
        @Override
        public void run() {
            // infinite loop check value
            int thresholdDifference = Constants.MAX_DIFF_COUNT_IN_MANUAL;

            try {
                int diffCount = 0;
                Log.i(LOG_TAG, "Start loop");
                while(loopFlag) {
                    Log.d(LOG_TAG, "Control mode = " + stateInUI.getControlMode().name());
                    // Wait to update ui
                    if (stateInUI.getControlMode() == ControlMode.AUTO) {
                        // Wait to update ui in auto mode
                        thresholdDifference = Constants.MAX_DIFF_COUNT_IN_AUTO;
                        Thread.sleep(Constants.WAIT_TO_STATE_CHANGE);
                    } else {
                        // Wait to update ui in manual mode
                        thresholdDifference = Constants.MAX_DIFF_COUNT_IN_MANUAL;
                        Thread.sleep(Constants.WAIT_TO_STATE_CHANGE);
                    }

                    // Check difference
                    if (checkStateBetweenExpectedAndUI()) {
                        diffCount++;
                        if (diffCount >= thresholdDifference) {
//                            showCannotRemoteDeviceDialog();
                            updateUI();
                            // reset diffCount
                            diffCount = 0;
                        }
                    } else {
                        diffCount = 0;
                    }
                }

                Log.i(LOG_TAG, "End of checker loop");
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

        }
    }

    /**
     * Stop refresh in refresh layout
     */
    public void stopRefresh() {
        if (swipeRefreshLayout.isRefreshing()) {
            parentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }
    }


    /**
     * Start refresh in refresh layout
     */
    private void startRefresh() {
        Log.i(LOG_TAG, "Start refresh layout");
        if (swipeRefreshLayout.isRefreshing()) {
            // Start refresh
            parentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(true);
                }
            });

            // disable all button
            uiInRefreshData();
            // check information
            parentActivity.checkInformation();
            // Refresh data
            monitoringSystem.readAndDisplayStatus(aqStatus, txtAQValue, txtAQTitle, txtAQLevel, gdView1, gdView2, gdView3, loadingBar, dsIcon, dsText);
            // update ui
            updateUI();
        }
    }

}