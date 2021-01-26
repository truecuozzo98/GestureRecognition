package com.example.gesturerecognition;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Led;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int GPS_ENABLED = 2;
    private static final int STORAGE_REQUEST_CODE = 3;
    private static final int LOCATION_PERMISSION = 5;

    public static BluetoothDevice bluetoothDevice;
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "CD:49:78:BF:7C:89";
    //private final String MW_MAC_ADDRESS= "EC:2C:09:81:22:AC";

    private MetaWearBoard board;
    private Accelerometer accelerometer;
    private ArrayList<JSONObject> dataSensor = new ArrayList<JSONObject>();
    private ParcelUuid metawearUuid = ParcelUuid.fromString(String.valueOf(MetaWearBoard.METAWEAR_GATT_SERVICE));

    private BluetoothAdapter bluetoothAdapter;

    private GPSBroadcastReceiver gpsBroadcastReceiver = new GPSBroadcastReceiver();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class), this, Context.BIND_AUTO_CREATE);

        Button connect = findViewById(R.id.buttonConnect);
        connect.setOnClickListener(v -> connectTo());

        Button led = findViewById(R.id.buttonLed);
        led.setOnClickListener(v -> blinkLed());

        Button start = findViewById(R.id.buttonStart);
        start.setOnClickListener(v -> startAccMeasurement());

        Button stop = findViewById(R.id.buttonStop);
        stop.setOnClickListener(v -> stopAccMeasurement());


        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        //connectedDevices = new ArrayList<>();

        registerReceiver(gpsBroadcastReceiver, new IntentFilter("android.location.PROVIDERS_CHANGED"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_OK) {
                    Log.d("BleDevice", "Bluetooth enabled");

                    if(checkLocationPermission() && checkGpsEnabled()){
                        toFragmentBleDevice();
                    }
                }
                else {
                    Log.d("BleDevice", "Bluetooth NOT enabled");
                    Toast.makeText(this, "Per connettere dispositivi Bluetooth è necessario attivare il Bluetooth.", Toast.LENGTH_LONG).show();
                }
                break;

            case GPS_ENABLED:
                if(resultCode == RESULT_OK) {
                    Log.d("PROVA", "Permessi location OK");
                    if(checkLocationPermission() && checkGpsEnabled())
                        toFragmentBleDevice();
                }
                break;
        }
    }


    public void toFragmentBleDevice() {
        Log.d("fragmentLOG", "toFragment");
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container_ble, new BleDeviceListFragment(), "fragmentBleDevice");
        fragmentTransaction.commitAllowingStateLoss();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        Log.d("board", "onServiceConnected");
        serviceBinder = (BtleService.LocalBinder) service;

        //retrieveBoard();
    }

    public void retrieveBoard(String deviceName) {
        BleDeviceListFragment bleDevice = new BleDeviceListFragment();
        bluetoothDevice = bleDevice.getDevice(deviceName);

        String deviceAddress = null;
        if(!bluetoothDevice.getName().isEmpty() && bluetoothDevice.getName().equals("MetaWear")) {
            deviceAddress = bluetoothDevice.getAddress();
        }

        Log.d("board", deviceAddress);
        if(deviceAddress == null) {
            return;
        }

        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(deviceAddress);

        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);

        board.onUnexpectedDisconnect(new MetaWearBoard.UnexpectedDisconnectHandler() {
            @Override
            public void disconnected(int status) {
                Log.i("board", "Unexpectedly lost connection: " + status);
            }
        });

        connectBoard();
    }

    public void blinkLed() {
        if (!board.isConnected()){
            Toast.makeText(MainActivity.this, "Please connect your sensor first", Toast.LENGTH_LONG).show();
            return;
        }

        Led led;
        if ((led = board.getModule(Led.class)) != null) {
            led.editPattern(Led.Color.BLUE, Led.PatternPreset.BLINK)
                    .repeatCount((byte) 2)
                    .commit();
            led.play();
        }
    }

    public void connectTo(){
        if (bluetoothAdapter == null) {
            Log.d("BleDevice", "Bluetooth non supportato");
        }
        else if (!bluetoothAdapter.isEnabled()) {
            Log.d("BleDevice", "Bluetooth non attivo");
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
        }
        else {
            Log.d("BleDevice", "Bluetooth attivo");
            //requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);

            if(checkLocationPermission() && checkGpsEnabled()) {
                toFragmentBleDevice();
                Log.d("BleDevice", "gps enabled");
            } else {
                requestLocationPermissions();
                Log.d("BleDevice", "requestLocation");
            }


        }

        /*board.connectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                if (task.isFaulted()) {
                    Log.d("board", "Failed to connect");
                    return null;
                }

                try {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sensor connected", Toast.LENGTH_SHORT).show());
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Log.d("board", "Connected");
                Log.d("board", "board model = " + board.getModel());

                board.readDeviceInformationAsync()
                        .continueWith(new Continuation<DeviceInformation, Void>() {
                            @Override
                            public Void then(Task<DeviceInformation> task) throws Exception {
                                Log.i("board", "Device Information: " + task.getResult());
                                return null;
                            }
                        });

                return null;
            }
        });*/
    }

    public void connectBoard() {
        board.connectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                if (task.isFaulted()) {
                    Log.d("board", "Failed to connect");
                    return null;
                }

                try {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sensor connected", Toast.LENGTH_SHORT).show());
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Log.d("board", "Connected");
                Log.d("board", "board model = " + board.getModel());

                board.readDeviceInformationAsync()
                        .continueWith(new Continuation<DeviceInformation, Void>() {
                            @Override
                            public Void then(Task<DeviceInformation> task) throws Exception {
                                Log.i("board", "Device Information: " + task.getResult());
                                return null;
                            }
                        });

                return null;
            }
        });
    }

    public void startAccMeasurement() {
        if (!board.isConnected()){
            Toast.makeText(MainActivity.this, "Sensor must be connected before starting measurement", Toast.LENGTH_LONG).show();
            return;
        }

        accelerometer = board.getModule(Accelerometer.class);
        accelerometer.configure()
                .odr(5f)       // Set sampling frequency to 25Hz, or closest valid ODR
                .range(4f)      // Set data range to +/-4g, or closet valid range
                .commit();
        accelerometer.start();

        accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.stream(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i("board", data.value(Acceleration.class).toString());
                        Log.i("board", String.valueOf(data.timestamp().getTimeInMillis()));

                        JSONObject object = new JSONObject();
                        try {
                            object.put("timestamp", String.valueOf(data.timestamp().getTimeInMillis()));
                            object.put("x", String.valueOf(data.value(Acceleration.class).x()));
                            object.put("y", String.valueOf(data.value(Acceleration.class).y()));
                            object.put("z", String.valueOf(data.value(Acceleration.class).z()));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        dataSensor.add(object);
                    }
                });
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                accelerometer.acceleration().start();
                accelerometer.start();
                return null;
            }
        });
        Toast.makeText(MainActivity.this, "Measurement started", Toast.LENGTH_SHORT).show();
    }

    public void stopAccMeasurement(){
        if(board == null || accelerometer == null) { return; }

        accelerometer.stop();
        Toast.makeText(MainActivity.this, "Measurement stopped", Toast.LENGTH_SHORT).show();

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            (new Handler()).postDelayed(this::writeDataOnDevice, 200);

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_PERMISSION:
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                /*if(checkLocationPermission()){
                    toFragmentBleDevice();
                }*/
                break;
            case STORAGE_REQUEST_CODE:
                writeDataOnDevice();
        }
    }

    public void writeDataOnDevice() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download", "MyAppDataSensor");
        if(!dir.exists()){
            dir.mkdir();
        }

        try {
            File gpxfile = new File(dir, "Data Sensor - " + Calendar.getInstance().getTimeInMillis());
            FileWriter writer = new FileWriter(gpxfile);

            writer.append("timestamp, x-axis, y-axis, z-axis\n");
            for(JSONObject a : dataSensor){
                String timestamp = (String) a.get("timestamp") + ", ";
                String x = (String) a.get("x") + ", ";
                String y = (String) a.get("y") + ", ";
                String z = (String) a.get("z") + "\n";
                writer.append(timestamp).append(x).append(y).append(z);
            }
            writer.flush();
            writer.close();

            Toast.makeText(MainActivity.this, "Your file has been saved in folder " + dir, Toast.LENGTH_LONG).show();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("board", "onDestroy");

        board.tearDown();
        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d("board", "onServiceDisconnected");

        board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                Log.i("board", "Disconnected");
                return null;
            }
        });
    }

    public boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkGpsEnabled() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d("GPSBroadcastReceiver", "FALSE");
            return false;
        }
        else {
            Log.d("GPSBroadcastReceiver", "TRUE");
            return true;
        }
    }


    public void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION);
    }

    public class GPSBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Log.d("GPSBroadcastReceiver", "Broadcast receiver");
                    if(checkLocationPermission() && checkGpsEnabled()){
                        toFragmentBleDevice();
                    }
                } else {
                    //isGPSEnabled = false;
                }
            }catch (Exception ex){
                Log.d("GPSBroadcastReceiver", ex.toString());
            }
        }
    }

}