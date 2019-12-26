package com.viettel.vht.remoteapp.objects;

import com.viettel.vht.remoteapp.common.ControlMode;
import com.viettel.vht.remoteapp.common.PowerState;
import com.viettel.vht.remoteapp.common.SpeedState;

public class RemoteDevice {
    private String remoteDeviceId;
    private String smartPlugId;


    public RemoteDevice() {
        smartPlugId = null;
        remoteDeviceId = null;
    }

    public void setAllNull() {
        smartPlugId = null;
        remoteDeviceId = null;
    }


    public boolean isNotNull() {
        if (smartPlugId == null || remoteDeviceId == null) {
            return false;
        }
        return true;
    }
    // Getter and setter
    public String getRemoteDeviceId() {
        return remoteDeviceId;
    }

    public void setRemoteDeviceId(String remoteDeviceId) {
        this.remoteDeviceId = remoteDeviceId;
    }

    public String getSmartPlugId() {
        return smartPlugId;
    }

    public void setSmartPlugId(String smartPlugId) {
        this.smartPlugId = smartPlugId;
    }
    
}
