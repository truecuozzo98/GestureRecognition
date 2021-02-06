package com.example.gesturerecognition;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

public class BleDeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    TextView deviceName;
    BleDeviceListFragment bleDevice;
    ConstraintLayout bluetoothItem;
    private AdapterCallback adapterCallback;

    public BleDeviceViewHolder(@NonNull View itemView, BleDeviceListFragment bleDevice, Context context) {
        super(itemView);
        deviceName = itemView.findViewById(R.id.device_name);
        bluetoothItem = itemView.findViewById(R.id.bluetoothItem);
        bluetoothItem.setOnClickListener(this);
        this.bleDevice = bleDevice;
        adapterCallback = ((AdapterCallback) context);
    }

    public void setCell(String name) {
        deviceName.setText(name);
    }

    @Override
    public void onClick(View v) {
        String deviceName = this.deviceName.getText().toString();
        Model.getInstance().setConnectedDeviceName(deviceName);
        adapterCallback.onMethodCallback();
    }

    public interface AdapterCallback {
        void onMethodCallback();
    }
}