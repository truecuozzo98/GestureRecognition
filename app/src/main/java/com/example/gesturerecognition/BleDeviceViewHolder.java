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
    private final Context mContext;

    public BleDeviceViewHolder(@NonNull View itemView, BleDeviceListFragment bleDevice, Context context) {
        super(itemView);
        deviceName = itemView.findViewById(R.id.device_name);
        bluetoothItem = itemView.findViewById(R.id.bluetoothItem);
        bluetoothItem.setOnClickListener(this);
        this.bleDevice = bleDevice;
        mContext = context;
    }

    public void setCell(String name) {
        deviceName.setText(name);
    }

    @Override
    public void onClick(View v) {
        String deviceName = this.deviceName.getText().toString();
        BleDeviceListFragment.connectedDeviceName = deviceName;

        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).retrieveBoard(deviceName);
        }

/*      FragmentManager fragmentManager = ((AppCompatActivity)v.getContext()).getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag("fragmentBleDevice");
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.remove(fragment).commit();*/

        /*BleDeviceListFragment bleDevice = new BleDeviceListFragment();
        BluetoothDevice bluetoothDevice = bleDevice.getDevice(deviceName);

        if(bluetoothDevice != null) {
            FragmentManager fragmentManager = ((AppCompatActivity)v.getContext()).getSupportFragmentManager();
            Fragment fragment = fragmentManager.findFragmentByTag("fragmentBleDevice");
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.remove(fragment).commit();
        }*/
    }
}