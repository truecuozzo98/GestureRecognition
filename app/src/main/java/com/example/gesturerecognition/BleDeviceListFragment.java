package com.example.gesturerecognition;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mbientlab.metawear.MetaWearBoard;

import java.util.ArrayList;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

// Fragment per la scansione dei dispositivi BLE
public class BleDeviceListFragment extends Fragment {
    private boolean scanning = false;
    private final Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    //TODO: rivedere static
    public static ArrayList<BluetoothDevice> devices = new ArrayList<>();
    BluetoothLeScannerCompat bluetoothLeScanner;
    ScanSettings scanSettings;
    List<ScanFilter> scanFilters = new ArrayList<>();
    ParcelUuid metawearUuid = ParcelUuid.fromString(String.valueOf(MetaWearBoard.METAWEAR_GATT_SERVICE));
    RecyclerView recyclerView;
    BleDeviceAdapter bleDeviceAdapter;
    BluetoothAdapter bluetoothAdapter;

    public String connectedDeviceName = "";
    private Context mContext;

    ConstraintLayout constraintLayoutMain, constraintLayout;
    ImageButton refreshBtn, closeBtn;
    TextView noBleDevice, connectedDeviceNameTv;
    Model model = Model.getInstance();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ble_device, container, false);
        noBleDevice = v.findViewById(R.id.no_ble_device);
        connectedDeviceNameTv = v.findViewById(R.id.connected_device_name);
        constraintLayoutMain = v.findViewById(R.id.constraintLayoutMain);
        constraintLayoutMain.setOnClickListener(onClickListener);
        constraintLayout = v.findViewById(R.id.connected_device);
        constraintLayout.setOnClickListener(onClickListener);
        constraintLayoutMain = v.findViewById(R.id.constraintLayoutMain);
        constraintLayoutMain.setOnClickListener(onClickListener);
        refreshBtn = v.findViewById(R.id.refresh_btn);
        refreshBtn.setOnClickListener(onClickListener);
        closeBtn = v.findViewById(R.id.close_btn);
        closeBtn.setOnClickListener(onClickListener);


        // Imposto RecyclerView e Adapter
        recyclerView = v.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        bleDeviceAdapter = new BleDeviceAdapter(this, mContext);
        recyclerView.setAdapter(bleDeviceAdapter);
        bleDeviceAdapter.notifyDataSetChanged();

        //requireActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Se BLE non è supportato, chiudo il fragment
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getContext(), "BLE non supportato", Toast.LENGTH_SHORT).show();
            Fragment fragment = getFragmentManager().findFragmentByTag("fragmentBleDevice");
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.remove(fragment).commit();
        }
        else {
            final BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
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
        removeFragment("fragmentBleDevice");

        if(scanning) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        removeFragment("fragmentBleDevice");

        if(scanning){
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    public void startScan() {
        String name = model.getConnectedDeviceName();
        if (name != null && !name.equals("")){
            constraintLayout.setVisibility(View.VISIBLE);
            connectedDeviceNameTv.setText(name);
        } else {
            constraintLayout.setVisibility(View.GONE);
        }

        recyclerView.removeAllViewsInLayout();
        noBleDevice.setVisibility(View.VISIBLE);

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
            if(bleDeviceAdapter.getItemCount() == 0) {
                noBleDevice.setVisibility(View.VISIBLE);
            } else {
                noBleDevice.setVisibility(View.GONE);
            }
            addDevice(result.getDevice());
        }

        @Override
        public void onBatchScanResults(@NonNull List<ScanResult> results) {
            super.onBatchScanResults(results);
            for(ScanResult scanResult : results) {
                addDevice(scanResult.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    public void addDevice(BluetoothDevice bluetoothDevice) {
        if(!devices.contains(bluetoothDevice)){
            if(bluetoothDevice.getName() != null) {
                noBleDevice.setVisibility(View.GONE);
                devices.add(bluetoothDevice);
            }
        }
        bleDeviceAdapter.notifyDataSetChanged();
    }

    public BluetoothDevice getDevice(String deviceName) {
        for(BluetoothDevice bluetoothDevice : devices)
            if(bluetoothDevice.getName().equals(deviceName))
                return bluetoothDevice;
        return null;
    }

    public void removeFragment(String tag) {
        assert getFragmentManager() != null;
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        assert fragment != null;
        transaction.remove(fragment).commit();
    }

    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.refresh_btn:
                    startScan();
                    break;
                case R.id.close_btn:
                case R.id.connected_device:
                case R.id.constraintLayoutMain:
                    removeFragment("fragmentBleDevice");
                    if (scanning) {
                        bluetoothLeScanner.stopScan(scanCallback);
                    }
                    break;
            }
        }
    };
}

