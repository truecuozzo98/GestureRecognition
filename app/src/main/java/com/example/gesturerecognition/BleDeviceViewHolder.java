package com.example.gesturerecognition;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
import com.mbientlab.metawear.android.BtleService;

import java.util.ArrayList;
import java.util.Objects;

public class BleDeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    TextView device_name;
    BleDeviceListFragment bleDevice;
    ConstraintLayout bluetoothItem;
    private Context mContext;

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

        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).retrieveBoard(deviceName);
        }

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