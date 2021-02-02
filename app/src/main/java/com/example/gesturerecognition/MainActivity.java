package com.example.gesturerecognition;

import android.Manifest;
import android.app.AlertDialog;
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
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
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
    private static final int CONNECTION_DIALOG = 1;
    private static final int RETRY_CONNECTION_DIALOG = 2;
    private static final String PATH_DIR = "/Download/GestureRecognition/";

    public static BluetoothDevice bluetoothDevice;
    private BtleService.LocalBinder serviceBinder;
    //private final String MW_MAC_ADDRESS= "CD:49:78:BF:7C:89";

    private MetaWearBoard board;
    private Accelerometer accelerometer;
    private final ArrayList<JSONObject> accelerometerDataJSON = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;

    private final GPSBroadcastReceiver gpsBroadcastReceiver = new GPSBroadcastReceiver();
    private EasyCsv easyCsv;

    double timestamp = 0;
    int gesture_counter = 0;
    static boolean starting_value_found;

    private AlertDialog connectDialog;
    public ArrayList<Gesture> gesture_list = new ArrayList<>();
    public static ArrayList<JSONObject> all_gesture_list = new ArrayList<>();

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

        Button disconnect = findViewById(R.id.disconnect_button);
        disconnect.setOnClickListener(v -> disconnectBoard());

        Button led = findViewById(R.id.buttonLed);
        led.setOnClickListener(v -> blinkLed());

        Button start = findViewById(R.id.buttonStart);
        start.setOnClickListener(v -> startAccMeasurement());

        Button stop = findViewById(R.id.buttonStop);
        stop.setOnClickListener(v -> stopAccMeasurement());

        Button gesture_list_button = findViewById(R.id.gesture_list_button);
        gesture_list_button.setOnClickListener(v -> toGestureListFragment());

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        registerReceiver(gpsBroadcastReceiver, new IntentFilter("android.location.PROVIDERS_CHANGED"));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

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

    public void removeFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null) {
            getSupportFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    public void toFragmentBleDevice() {
        Fragment fragment = new BleDeviceListFragment();

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container_ble, fragment, "fragmentBleDevice");
        fragmentTransaction.addToBackStack("fragmentBleDevice");
        fragmentTransaction.commit();
    }

    public void toGestureListFragment() {
        Fragment fragment = new GestureListFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container_ble, fragment, "gestureListFragment");
        fragmentTransaction.addToBackStack("gestureListFragment");
        fragmentTransaction.commit();
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
        createNewDialog(CONNECTION_DIALOG).show();

        board.connectAsync().continueWith((Continuation<Void, Void>) task -> {
            if (task.isFaulted()) {
                Log.d("board", "Failed to connect");
                if(connectDialog != null && connectDialog.isShowing()) {
                    connectDialog.dismiss();
                }
                createNewDialog(RETRY_CONNECTION_DIALOG).show();

                BleDeviceListFragment.connected_device_name = "";
                disconnectBoard();
                return null;
            }

            if(connectDialog != null && connectDialog.isShowing()) {
                connectDialog.dismiss();
            }
            removeFragment("fragmentBleDevice");

            //Toast.makeText(MainActivity.this, "Sensor connected", Toast.LENGTH_SHORT).show();
            try {
                runOnUiThread(() -> {
                    TextView tv = findViewById(R.id.status);
                    tv.setText("Connected");
                    tv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.emerald));

                    tv = findViewById(R.id.device_name_main_activity);
                    tv.setVisibility(View.VISIBLE);
                    String text = "Device name: " + BleDeviceListFragment.connected_device_name;
                    tv.setText(text);
                });
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
        if (board == null || !board.isConnected()){
            Toast.makeText(MainActivity.this, "Sensor must be connected before starting measurement", Toast.LENGTH_LONG).show();
            return;
        }

        TextView tv = findViewById(R.id.counter_gestures);
        tv.setText(String.valueOf(0));

        timestamp = 0;
        gesture_counter = 0;
        starting_value_found = false;
        gesture_list.clear();
        accelerometerDataJSON.clear();

        GestureRecognizer gestureRecognizer = new GestureRecognizer(-1.1, 0.6, 3000);
        gestureRecognizer.addGestureEventListener(new GestureEventListener() {
            @Override
            public void onGesture(double timestamp_start, double timestamp_ending) {
                gesture_counter += 1;
                TextView tv = findViewById(R.id.counter_gestures);
                runOnUiThread(() -> tv.setText(String.valueOf(gesture_counter)));

                gesture_list.add(new Gesture("gesture_1", timestamp_start, timestamp_ending));
            }
        });

        accelerometer = board.getModule(Accelerometer.class);
        accelerometer.configure()
                .odr(5f)       // Set sampling frequency to 25Hz, or closest valid ODR
                .range(4f)      // Set data range to +/-4g, or closet valid range
                .commit();
        accelerometer.start();

        accelerometer.acceleration().addRouteAsync(source -> source.stream((Subscriber) (data, env) -> {
            long epoch = data.timestamp().getTimeInMillis();
            float x = data.value(Acceleration.class).x();
            float y = data.value(Acceleration.class).y();
            float z = data.value(Acceleration.class).z();

            if(!accelerometerDataJSON.isEmpty()) {
                int index = accelerometerDataJSON.size()-1;
                try {
                    timestamp += (epoch - accelerometerDataJSON.get(index).getDouble("epoch"))/1000;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            gestureRecognizer.recognizeGesture(x, timestamp);

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

            accelerometerDataJSON.add(object);
        })).continueWith((Continuation<Route, Void>) task -> {
            accelerometer.acceleration().start();
            accelerometer.start();
            return null;
        });
        Toast.makeText(MainActivity.this, "Measurement started", Toast.LENGTH_SHORT).show();
    }

    public void stopAccMeasurement() {
        if(board == null || accelerometer == null) { return; }

        if(!gesture_list.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            final String currentDateTime = sdf.format(new Date());

            JSONObject jsonObject = new JSONObject();
            try {
                //ArrayList<Gesture> copy = new ArrayList<>(gesture_list);
                jsonObject.put("date", currentDateTime);
                jsonObject.put("gesture_list", new ArrayList<>(gesture_list));

                all_gesture_list.add(jsonObject);

                for(JSONObject x : all_gesture_list) {
                    Log.d("allGesture", "x         : " + x.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //Log.d("allGesture", "JSONObject: " + jsonObject.toString());
        }

        accelerometer.stop();
        Toast.makeText(MainActivity.this, "Measurement stopped", Toast.LENGTH_SHORT).show();




        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            //writeDataOnDevice();
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
        if(!accelerometerDataJSON.isEmpty()) {
            easyCsv.createCsvFile(fileNameWithPath, headerList, fromJsonToStringCSV(accelerometerDataJSON), STORAGE_REQUEST_CODE, new FileCallback() {
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

    public List<String> fromJsonToStringCSV (ArrayList<JSONObject> jsonArray) {
        List<String> stringList = new ArrayList<>();
        for (JSONObject x : jsonArray) {
            try {
                stringList.add(x.get("timestamp") + "," + x.get("x") + "," + x.get("y") + "," + x.get("z") + ";");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return stringList;
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

        disconnectBoard();
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

            } catch (Exception e) {
                Log.d("GPSBroadcastReceiver", e.toString());
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // It means the user has changed his bluetooth state.
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.d("bluetoothLOG", "The user bluetooth is turning off yet, but it is not disabled yet.");
                    disconnectBoard();
                }

                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    Log.d("bluetoothLOG", "The user bluetooth is already disabled.");
                    disconnectBoard();
                }
            }
        }
    };

    public void disconnectBoard() {
        if(board != null) {
            board.disconnectAsync().continueWith((Continuation<Void, Void>) task -> {
                Log.i("board", "Disconnected");
                BleDeviceListFragment.connected_device_name = "";
                try {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Sensor disconnected", Toast.LENGTH_SHORT).show();
                        TextView tv = findViewById(R.id.status);
                        tv.setText("Not connected");
                        tv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));

                        tv = findViewById(R.id.device_name_main_activity);
                        tv.setVisibility(View.GONE);
                    });
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            });
        }
    }

    public AlertDialog createNewDialog(int type) {
        AlertDialog dlg = null;
        switch (type) {
            case CONNECTION_DIALOG:
                dlg = showConnectDialog();
                break;
            case RETRY_CONNECTION_DIALOG:
                dlg = retryConnectionDialog();
        }
        return dlg;
    }

    public AlertDialog showConnectDialog() {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View dialogView = layoutInflater.inflate(R.layout.alert_dialog_connect_metawear, null);
        connectDialog = new AlertDialog.Builder(this).create();
        connectDialog.setView(dialogView);

        connectDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        connectDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView device_tv = dialogView.findViewById((R.id.alert_dialog_connect_metawear_title));
        String text = "Connecting to " + BleDeviceListFragment.connected_device_name + "...";
        device_tv.setText(text);

        Button undo_btn = dialogView.findViewById(R.id.undo_btn);
        undo_btn.setOnClickListener(v -> {
            connectDialog.dismiss();
            disconnectBoard();
        });

        return connectDialog;
    }

    public AlertDialog retryConnectionDialog() {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View dialogView = layoutInflater.inflate(R.layout.alert_dialog_connect_metawear, null);
        connectDialog = new AlertDialog.Builder(this).create();
        connectDialog.setView(dialogView);

        connectDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        connectDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView title = dialogView.findViewById(R.id.alert_dialog_connect_metawear_title);
        TextView text = dialogView.findViewById(R.id.alert_dialog_connect_metawear_wait);
        Button undo_btn = dialogView.findViewById(R.id.undo_btn);

        title.setText("Connection failed");
        text.setVisibility(View.GONE);
        undo_btn.setText("Retry");

        undo_btn.setOnClickListener(v -> {
            connectDialog.dismiss();
            createNewDialog(CONNECTION_DIALOG);
            connectBoard();
        });

        return connectDialog;
    }
}

