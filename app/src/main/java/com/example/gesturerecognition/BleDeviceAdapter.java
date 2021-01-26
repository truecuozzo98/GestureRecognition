package com.example.gesturerecognition;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceViewHolder> {
    private final BleDeviceListFragment bleDevice;
    private final LayoutInflater layoutInflater;
    private final Context mContext;

    public BleDeviceAdapter(BleDeviceListFragment bleDevice, Context context) {
        this.layoutInflater = LayoutInflater.from(bleDevice.getContext());
        this.bleDevice = bleDevice;
        mContext = context;
    }

    @NonNull
    @Override
    public BleDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = layoutInflater.inflate(R.layout.single_bluetooth_view, viewGroup, false);
        return new BleDeviceViewHolder(view, bleDevice, mContext);
    }

    @Override
    public void onBindViewHolder(@NonNull BleDeviceViewHolder holder, int position) {
        holder.setCell(BleDeviceListFragment.devices.get(position).getName());
    }

    @Override
    public int getItemCount() {
        return BleDeviceListFragment.devices.size();
    }
}