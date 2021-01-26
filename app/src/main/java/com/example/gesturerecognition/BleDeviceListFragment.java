package com.example.gesturerecognition;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ColorFilter;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;
import com.mbientlab.metawear.MetaWearBoard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

// Fragment per la scansione dei dispositivi BLE
public class BleDeviceListFragment extends Fragment {
    private boolean scanning = false;
    private final Handler handler = new Handler();
    public ArrayList<BluetoothDevice> list = new ArrayList<>();
    private static final long SCAN_PERIOD = 10000;
    BluetoothLeScannerCompat bluetoothLeScanner;
    ScanSettings scanSettings;
    List<ScanFilter> scanFilters = new ArrayList<>();
    ParcelUuid metawearUuid = ParcelUuid.fromString(String.valueOf(MetaWearBoard.METAWEAR_GATT_SERVICE));
    //Metawear UUID = 326a9000-85cb-9195-d9dd-464cfbbae75a

    public static ArrayList<BluetoothDevice> devices = new ArrayList<>();
    RecyclerView recyclerView;
    BleDeviceAdapter bleDeviceAdapter;
    BluetoothAdapter bluetoothAdapter;

    ConstraintLayout constraintLayoutMain;


    private Context mContext;
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ble_device, container, false);

        Log.d("fragmentLOG", "onCreateView");

        constraintLayoutMain = v.findViewById(R.id.constraintLayoutMain);
        constraintLayoutMain.setOnClickListener(onClickListener);

        // Imposto RecyclerView e Adapter
        recyclerView = v.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        bleDeviceAdapter = new BleDeviceAdapter(this, mContext);
        recyclerView.setAdapter(bleDeviceAdapter);
        bleDeviceAdapter.notifyDataSetChanged();

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("fragmentLOG", "onStart");

        // Se BLE non è supportato, chiudo il fragment
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getContext(), "BLE non supportato", Toast.LENGTH_SHORT).show();

            Fragment fragment = getFragmentManager().findFragmentByTag("fragmentBleDevice");
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.remove(fragment).commit();
        }
        else {
            //Toast.makeText(getContext(), "BLE supportato", Toast.LENGTH_SHORT).show();
            Log.d("fragmentLOG", "onStart else");

            final BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                Log.d("fragmentLOG", "if bluetoothAdapter");

                bluetoothLeScanner = BluetoothLeScannerCompat.getScanner();
                scanSettings = new ScanSettings.Builder()
                        .setLegacy(false)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .setUseHardwareBatchingIfSupported(true)
                        .build();

                // Permette di filtrare solamente i dispositivi di tipo MetaWear, altrimenti commentare questa riga
                scanFilters.add(new ScanFilter.Builder().setServiceUuid(metawearUuid).build());

                startScan();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        removeFragment();

        if(scanning)
            bluetoothLeScanner.stopScan(scanCallback);
    }

    @Override
    public void onStop() {
        super.onStop();
        removeFragment();

        if(scanning)
            bluetoothLeScanner.stopScan(scanCallback);
    }

    public void startScan() {
        if(!scanning) {
            devices.clear();
            scanning = true;
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);
        }

        handler.postDelayed(() -> {
            scanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
        }, SCAN_PERIOD);
    }

    ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            super.onScanResult(callbackType, result);
            //Log.d("PROVA", "Result: " + result.toString());
            addDevice(result.getDevice());
        }

        @Override
        public void onBatchScanResults(@NonNull List<ScanResult> results) {
            super.onBatchScanResults(results);
            //Log.d("PROVA", "Results: " + results.toString());

            for(ScanResult scanResult : results)
                addDevice(scanResult.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    public void addDevice(BluetoothDevice bluetoothDevice) {
        if(!devices.contains(bluetoothDevice))
            if(bluetoothDevice.getName() != null)
                devices.add(bluetoothDevice);

        bleDeviceAdapter.notifyDataSetChanged();

        //Log.d("PROVA", "LISTA: " + devices.toString());
    }

    public BluetoothDevice getDevice(String deviceName) {
        for(BluetoothDevice bluetoothDevice : devices)
            if(bluetoothDevice.getName().equals(deviceName))
                return bluetoothDevice;
        return null;
    }

    public void removeFragment() {
        Fragment fragment = getFragmentManager().findFragmentByTag("fragmentBleDevice");
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.remove(fragment).commit();
    }

    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.constraintLayoutMain:
                    removeFragment();
                    if(scanning)
                        bluetoothLeScanner.stopScan(scanCallback);
                    break;
            }
        }
    };
}

