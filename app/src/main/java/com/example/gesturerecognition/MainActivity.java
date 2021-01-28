package com.example.gesturerecognition;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Led;

import net.ozaydin.serkan.easy_csv.EasyCsv;
import net.ozaydin.serkan.easy_csv.FileCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import bolts.Continuation;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int GPS_ENABLED = 2;
    private static final int STORAGE_REQUEST_CODE = 3;
    private static final int LOCATION_PERMISSION = 5;
    private static final String PATH_DIR = "/Download/GestureRecognition/";

    public static BluetoothDevice bluetoothDevice;
    private BtleService.LocalBinder serviceBinder;
    //private final String MW_MAC_ADDRESS= "CD:49:78:BF:7C:89";

    private MetaWearBoard board;
    private Accelerometer accelerometer;
    private final ArrayList<JSONObject> accelerometerDataJSON = new ArrayList<>();
    private final List<String> accelerometerDataString = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;

    private final GPSBroadcastReceiver gpsBroadcastReceiver = new GPSBroadcastReceiver();
    private EasyCsv easyCsv;

    double timestamp = 0;
    double last_gesture_timestamp = 0;
    int gesture_counter = 0;

    double lower_threshold = -1.1;
    double upper_threshold = 0.6;
    double timestamp_lower_threshold;
    double timestamp_upper_threshold;
    double gesture_duration = 3000;


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

        registerReceiver(gpsBroadcastReceiver, new IntentFilter("android.location.PROVIDERS_CHANGED"));

        easyCsv = new EasyCsv(this);
        easyCsv.setSeparatorColumn(",");
        easyCsv.setSeperatorLine(";");
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

        board.onUnexpectedDisconnect(status -> Log.i("board", "Unexpectedly lost connection: " + status));

        connectBoard();
    }

    public void blinkLed() {
        if (board == null || !board.isConnected()){
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

            if(checkLocationPermission() && checkGpsEnabled()) {
                toFragmentBleDevice();
                Log.d("BleDevice", "gps enabled");
            } else {
                requestLocationPermissions();
                Log.d("BleDevice", "requestLocation");
            }
        }
    }

    public void connectBoard() {
        board.connectAsync().continueWith((Continuation<Void, Void>) task -> {
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

            Log.d("board", "Connected, board model: " + board.getModel());

            board.readDeviceInformationAsync()
                    .continueWith((Continuation<DeviceInformation, Void>) task1 -> {
                        Log.i("board", "Device Information: " + task1.getResult());
                        return null;
                    });

            return null;
        });
    }

    public void startAccMeasurement() {
        accelerometerDataJSON.clear();
        accelerometerDataString.clear();
        timestamp = 0;
        gesture_counter = 0;
        timestamp_lower_threshold = 0;

        if (board == null || !board.isConnected()){
            Toast.makeText(MainActivity.this, "Sensor must be connected before starting measurement", Toast.LENGTH_LONG).show();
            return;
        }

        accelerometer = board.getModule(Accelerometer.class);
        accelerometer.configure()
                .odr(5f)       // Set sampling frequency to 25Hz, or closest valid ODR
                .range(4f)      // Set data range to +/-4g, or closet valid range
                .commit();
        accelerometer.start();

        accelerometer.acceleration().addRouteAsync(source -> source.stream((Subscriber) (data, env) -> {
            long epoch = data.timestamp().getTimeInMillis();

            if(!accelerometerDataJSON.isEmpty()) {
                int index = accelerometerDataJSON.size()-1;
                try {
                    timestamp += (epoch - accelerometerDataJSON.get(index).getDouble("epoch"))/1000;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            float x = data.value(Acceleration.class).x();
            float y = data.value(Acceleration.class).y();
            float z = data.value(Acceleration.class).z();

            JSONObject object = new JSONObject();
            try {
                object.put("epoch", epoch);
                object.put("timestamp", timestamp);
                object.put("x", x);
                object.put("y", y);
                object.put("z", z);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if(x < lower_threshold) {
                timestamp_lower_threshold = epoch;
                //Log.d("recognize", "inside if lower; x: " + x + " timestamp_lower: " + timestamp_lower_threshold);
            }

            double diff = (epoch - timestamp_lower_threshold);

            if(x > upper_threshold &&  diff < gesture_duration) {
                Log.d("recognize", "timestamp_lower_threshold: " + timestamp_lower_threshold + " timestamp: " + epoch + " diff: " + diff);
                if(recognizeGesture1(epoch, x)) {
                    gesture_counter += 1;
                    Log.d("recognizeGesture1", "gesture 1 recognized at timestamp: " + timestamp);
                    timestamp_lower_threshold = 0 ;
                    blinkLed();
                }
            } else {
                //Log.d("recognize", "timestamp_lower_threshold: " + timestamp_lower_threshold + " timestamp: " + epoch + " diff: " + diff);
            }

            accelerometerDataJSON.add(object);
            accelerometerDataString.add(timestamp + "," + x + "," + y + "," + z + ";");
        })).continueWith((Continuation<Route, Void>) task -> {
            accelerometer.acceleration().start();
            accelerometer.start();
            return null;
        });
        Toast.makeText(MainActivity.this, "Measurement started", Toast.LENGTH_SHORT).show();
    }

    public boolean recognizeGesture1(double current_timestamp, double axix_data) {
        double cooldown = 1000; //cooldown tra un gesto e l'altro di 1s

        if(/*axix_data >= upper_threshold && */(current_timestamp - last_gesture_timestamp) >= cooldown) {
            last_gesture_timestamp = current_timestamp;
            return true;
        }

        return false;
    }

    public void stopAccMeasurement() {
        if(board == null || accelerometer == null) { return; }

        accelerometer.stop();
        Toast.makeText(MainActivity.this, "Measurement stopped", Toast.LENGTH_SHORT).show();

        TextView tv = findViewById(R.id.counter_gestures);
        tv.setText(String.valueOf(gesture_counter));

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            writeDataOnDevice();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION:
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                if(checkLocationPermission()){
                    toFragmentBleDevice();
                }
                break;
            case STORAGE_REQUEST_CODE:
                writeDataOnDevice();
                break;
        }
    }

    public void writeDataOnDevice() {
        List<String> headerList = new ArrayList<>();
        headerList.add("Timestamp,x-axis,y-axis,z-axis;");

        File accelerometerDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + File.separator + "GestureRecognition"), "Accelerometer");
        if(!accelerometerDir.exists()) accelerometerDir.mkdirs();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        final String currentDateTime = sdf.format(new Date());
        final String fileNameWithPath = PATH_DIR + "Accelerometer/registration_" + currentDateTime;

        // Scrittura dati accelerometro
        if(!accelerometerDataString.isEmpty()) {
            easyCsv.createCsvFile(fileNameWithPath, headerList, accelerometerDataString, STORAGE_REQUEST_CODE, new FileCallback() {
                @Override
                public void onSuccess(File file) {
                    Log.d("EasyCsv", "Accelerometro file salvato: " + file.getName());
                    Toast.makeText(MainActivity.this, "Your file has been saved in folder " + file.getName(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFail(String err) {
                    Log.d("EasyCsv", "Accelerometro Errore: " + err);
                    Toast.makeText(MainActivity.this, "ERROR: your file could not be saved", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("board", "onDestroy");

        if(board != null) {
            board.tearDown();
        }

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d("board", "onServiceDisconnected");

        board.disconnectAsync().continueWith((Continuation<Void, Void>) task -> {
            Log.i("board", "Disconnected");
            return null;
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
                }

            }catch (Exception e){
                Log.d("GPSBroadcastReceiver", e.toString());
            }
        }
    }

}