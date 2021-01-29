package com.example.gesturerecognition;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

public class BleDeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    TextView device_name;
    BleDeviceListFragment bleDevice;
    ConstraintLayout bluetoothItem;
    private final Context mContext;

    public BleDeviceViewHolder(@NonNull View itemView, BleDeviceListFragment bleDevice, Context context) {
        super(itemView);
        device_name = itemView.findViewById(R.id.device_name);
        bluetoothItem = itemView.findViewById(R.id.bluetoothItem);
        bluetoothItem.setOnClickListener(this);
        this.bleDevice = bleDevice;
        mContext = context;
    }

    public void setCell(String name) {
        device_name.setText(name);
    }

    @Override
    public void onClick(View v) {
        String deviceName = device_name.getText().toString();
        BleDeviceListFragment.connected_device_name = deviceName;

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