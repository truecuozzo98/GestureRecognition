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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
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

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.data.EulerAngles;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;

import net.ozaydin.serkan.easy_csv.EasyCsv;
import net.ozaydin.serkan.easy_csv.FileCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection, AdapterView.OnItemSelectedListener, BleDeviceViewHolder.AdapterCallback {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int GPS_ENABLED = 2;
    private static final int STORAGE_REQUEST_CODE = 3;
    private static final int LOCATION_PERMISSION = 5;
    private static final int CONNECTION_DIALOG = 1;
    private static final int RETRY_CONNECTION_DIALOG = 2;
    private static final String PATH_DIR = "/Download/GestureRecognition/";

    public static BluetoothDevice bluetoothDevice;
    private BluetoothAdapter bluetoothAdapter;
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "CD:49:78:BF:7C:89";

    private MetaWearBoard board;
    private Accelerometer accelerometer;
    private GyroBmi160 gyro;
    /*double timestamp = 0;
    double previousTimestamp = 0;
    private final ArrayList<JSONObject> accelerometerDataJSON = new ArrayList<>();
    private final ArrayList<JSONObject> gyroscopeDataJSON = new ArrayList<>();*/
    private final List<String> accelerometerDataString = new ArrayList<>();
    private final List<String> gyroscopeDataString = new ArrayList<>();
    public ArrayList<RecognizedGesture> recognizedGestureList = new ArrayList<>();

    private final GPSBroadcastReceiver gpsBroadcastReceiver = new GPSBroadcastReceiver();

    private EasyCsv easyCsv;

    private AlertDialog connectDialog;
    
    private final Model model = Model.getInstance();
    private GestureRecognizer gestureRecognizer;

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

        Button disconnect = findViewById(R.id.disconnectButton);
        disconnect.setOnClickListener(v -> disconnectBoard());

        Button led = findViewById(R.id.buttonLed);
        led.setOnClickListener(v -> blinkLed());

        Button start = findViewById(R.id.buttonStart);
        start.setOnClickListener(v -> startGettingData());

        Button stop = findViewById(R.id.buttonStop);
        stop.setOnClickListener(v -> stopGettingData());

        Button gestureListButton = findViewById(R.id.gestureListButton);
        gestureListButton.setOnClickListener(v -> goToFragment("gestureListFragment"));

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        registerReceiver(gpsBroadcastReceiver, new IntentFilter("android.location.PROVIDERS_CHANGED"));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        easyCsv = new EasyCsv(this);
        easyCsv.setSeparatorColumn(",");
        easyCsv.setSeperatorLine(";");

        Spinner spinner = findViewById(R.id.gesture_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.gesture_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_OK) {
                    Log.d("BleDevice", "Bluetooth enabled");

                    if(checkLocationPermission() && checkGpsEnabled()){
                        goToFragment("fragmentBleDevice");
                    }
                }
                else {
                    Log.d("BleDevice", "Bluetooth NOT enabled");
                    Toast.makeText(this, "Per connettere dispositivi Bluetooth è necessario attivare il Bluetooth.", Toast.LENGTH_LONG).show();
                }
                break;

            case GPS_ENABLED:
                if(resultCode == RESULT_OK) {
                    Log.d("BleDevice", "Permessi location OK");
                    if(checkLocationPermission() && checkGpsEnabled())
                        goToFragment("fragmentBleDevice");
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

    public void goToFragment(String tag) {
        Fragment fragment = null;
        switch (tag) {
            case "fragmentBleDevice":
                fragment = new BleDeviceListFragment();
                break;
            case "gestureListFragment":
                fragment = new GestureListFragment();
                break;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        assert fragment != null;
        fragmentTransaction.replace(R.id.fragmentContainerBle, fragment, tag);
        fragmentTransaction.addToBackStack("fragmentBleDevice");
        fragmentTransaction.commit();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d("board", "onServiceConnected");
        serviceBinder = (BtleService.LocalBinder) service;

        //TODO: rimuovere connessione automatica
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);
        connectBoard();
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
                goToFragment("fragmentBleDevice");
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
                model.setConnectedDeviceName("");
                disconnectBoard();
                return null;
            }

            if(connectDialog != null && connectDialog.isShowing()) {
                connectDialog.dismiss();
            }
            removeFragment("fragmentBleDevice");

            try {
                runOnUiThread(() -> {
                    TextView tv = findViewById(R.id.status);
                    tv.setText("Connected");
                    tv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.emerald));

                    tv = findViewById(R.id.deviceNameMainActivity);
                    tv.setVisibility(View.VISIBLE);
                    String text = "Device name: " + model.getConnectedDeviceName();
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

    public void startGettingData() {
        if (board == null || !board.isConnected()){
            Toast.makeText(MainActivity.this, "Sensor must be connected before starting measurement", Toast.LENGTH_LONG).show();
            return;
        }

        TextView tv = findViewById(R.id.counterGestures);
        tv.setText(String.valueOf(0));

        recognizedGestureList.clear();
        /*accelerometerDataJSON.clear();
        gyroscopeDataJSON.clear();*/
        accelerometerDataString.clear();
        gyroscopeDataString.clear();

        Spinner spinner = findViewById(R.id.gesture_spinner);
        String text = spinner.getSelectedItem().toString();

        gestureRecognizer = initGestureRecognizer(text);
        assert gestureRecognizer != null;
        gestureRecognizer.addGestureEventListener(new GestureEventListener() {
            @Override
            public void onGestureStarts(double timestampStart, double timestampEnding) {
                Log.d("onGestureStarts", "onGestureStarts, timestampStart: " + timestampStart + ", timestampEnding: " + timestampEnding);
                TextView tv = findViewById(R.id.counterGestures);
                int counter = Integer.parseInt(tv.getText().toString()) +1;
                runOnUiThread(() -> tv.setText(String.valueOf(counter)));
            }

            @Override
            public void onGestureEnds(RecognizedGesture rg) {
                Log.d("onGestureEnds", "onGestureEnds, rg:" + rg.toStringRoundedDecimal());
                recognizedGestureList.add(rg);
            }
        });

        if(gestureRecognizer.getSensor() == GestureRecognizer.SENSOR_ACCELEROMETER) {
            getAccelerometerData();
        }

        if(gestureRecognizer.getSensor() == GestureRecognizer.SENSOR_GYRO) {
            getGyroData();
        }

        /*getAccelerometerData(gestureRecognizer);
        getGyroData(gestureRecognizer);*/
        Toast.makeText(MainActivity.this, "Started", Toast.LENGTH_SHORT).show();
    }

    private GestureRecognizer initGestureRecognizer(String text) {
        try {
            JSONObject jsonObject = new JSONObject(loadJSONGestureParameters());
            JSONArray jsonArray = jsonObject.getJSONArray("gestures");

            for (int i = 0; i < jsonArray.length() ; i++) {
                String gestureName = jsonArray.getJSONObject(i).getString("name");
                if(text.toLowerCase().equals(gestureName.toLowerCase())) {
                    int axis = jsonArray.getJSONObject(i).getInt("axis");
                    boolean increasing = jsonArray.getJSONObject(i).getBoolean("increasing");
                    int sensor = jsonArray.getJSONObject(i).getInt("sensor");
                    double startingValue = jsonArray.getJSONObject(i).getDouble("startingValue");
                    double endingValue = jsonArray.getJSONObject(i).getDouble("endingValue");
                    double gestureDuration = jsonArray.getJSONObject(i).getDouble("gestureDuration");
                    return new GestureRecognizer(gestureName, axis, increasing, sensor, startingValue, endingValue, gestureDuration);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void getAccelerometerData() {
        accelerometer = board.getModule(Accelerometer.class);
        accelerometer.configure()
                .odr(5f)
                .range(4f)
                .commit();
        accelerometer.start();

        accelerometer.acceleration().addRouteAsync(source -> source.stream((Subscriber) (data, env) -> {
            gestureRecognizer.recognizeGesture(data);

            long epoch = data.timestamp().getTimeInMillis();
            double timestamp = gestureRecognizer.getFormattedTimestamp(epoch);
            float x = data.value(Acceleration.class).x();
            float y = data.value(Acceleration.class).y();
            float z = data.value(Acceleration.class).z();

            accelerometerDataString.add(epoch + "," + timestamp + "," + x + "," + y + "," + z + ";");
        })).continueWith((Continuation<Route, Void>) task -> {
            accelerometer.acceleration().start();
            accelerometer.start();
            return null;
        });
    }

    private void getGyroData() {
        gyro = board.getModule(GyroBmi160.class);
        gyro.configure()
                .odr(GyroBmi160.OutputDataRate.ODR_25_HZ)
                .range(GyroBmi160.Range.FSR_2000)
                .commit();
        gyro.start();

        gyro.angularVelocity().addRouteAsync(source -> source.stream((Subscriber) (data, env) -> {
            gestureRecognizer.recognizeGesture(data);

            long epoch = data.timestamp().getTimeInMillis();
            double timestamp = gestureRecognizer.getFormattedTimestamp(epoch);
            float x = data.value(AngularVelocity.class).x();
            float y = data.value(AngularVelocity.class).y();
            float z = data.value(AngularVelocity.class).z();
            gyroscopeDataString.add(epoch + "," + timestamp + "," + x + "," + y + "," + z + ";");
        })).continueWith((Continuation<Route, Void>) task -> {
            gyro.angularVelocity().start();
            gyro.start();
            return null;
        });
    }

    public void stopGettingData() {
        if(board == null) { return; }

        if(!recognizedGestureList.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            final String currentDateTime = sdf.format(new Date());

            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("date", currentDateTime);
                jsonObject.put("gestureList", new ArrayList<>(recognizedGestureList));
                model.addGestureSession(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if(accelerometer != null) {
            accelerometer.stop();
        }

        if(gyro != null) {
            gyro.stop();
        }

        /*if(gestureRecognizer.getSensor() == GestureRecognizer.SENSOR_ACCELEROMETER) {
            if(accelerometer != null) {
                accelerometer.stop();
                Log.d("TEST", "accelerometer stopped");
            }
        } else {
            if(gyro != null) {
                gyro.stop();
            }

            if(sensorFusion != null) {
                sensorFusion.stop();
            }
        }*/
        Toast.makeText(MainActivity.this, "Stopped", Toast.LENGTH_SHORT).show();

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            //TODO: implementare writeDataOnDevice in GestureRecognizer
            //writeDataOnDevice();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
        }
    }

    public String loadJSONGestureParameters() {
        String json;
        try {
            InputStream is = getAssets().open("gestures_parameters.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION:
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                if(checkLocationPermission()){
                    goToFragment("fragmentBleDevice");
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        final String currentDateTime = sdf.format(new Date());

        //Scrittura dati accelerometro
        if(!accelerometerDataString.isEmpty()) {
            File accelerometerDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + File.separator + "GestureRecognition"), "Accelerometer");
            if(!accelerometerDir.exists()) {
                accelerometerDir.mkdirs();
            }

            final String fileNameWithPath = PATH_DIR + "Accelerometer/registration_" + currentDateTime;
            //List<String> list = fromJsonToStringCSV(accelerometerDataJSON);
            easyCsv.createCsvFile(fileNameWithPath, headerList, accelerometerDataString, STORAGE_REQUEST_CODE, new FileCallback() {
                @Override
                public void onSuccess(File file) {
                    Log.d("EasyCsv", "Accelerometro file salvato: " + file.getName());
                    Toast.makeText(MainActivity.this, "Your file has been saved in folder " + fileNameWithPath, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFail(String err) {
                    Log.d("EasyCsv", "Accelerometro Errore: " + err);
                    Toast.makeText(MainActivity.this, "ERROR: your file could not be saved", Toast.LENGTH_LONG).show();
                }
            });
        }

        if(!gyroscopeDataString.isEmpty()) {
            File gyroDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + File.separator + "GestureRecognition"), "Gyroscope");
            if(!gyroDir.exists()) {
                gyroDir.mkdirs();
            }

            final String fileNameWithPath = PATH_DIR + "Gyroscope/registration_" + currentDateTime;
            //List<String> list = fromJsonToStringCSV(gyroscopeDataJSON);
            easyCsv.createCsvFile(fileNameWithPath, headerList, gyroscopeDataString, STORAGE_REQUEST_CODE, new FileCallback() {
                @Override
                public void onSuccess(File file) {
                    Log.d("EasyCsv", "Giroscopio file salvato: " + file.getName());
                    Toast.makeText(MainActivity.this, "Your file has been saved in folder " + fileNameWithPath, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFail(String err) {
                    Log.d("EasyCsv", "Giroscopio Errore: " + err);
                    Toast.makeText(MainActivity.this, "ERROR: your file could not be saved", Toast.LENGTH_LONG).show();
                }
            });
        }

    }

    /*public List<String> fromJsonToStringCSV (ArrayList<JSONObject> jsonArray) {
        List<String> stringList = new ArrayList<>();
        for (JSONObject x : jsonArray) {
            stringList.add("a");
            try {
                stringList.add(x.get("timestamp") + "," + x.get("x") + "," + x.get("y") + "," + x.get("z") + ";");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return stringList;
    }*/

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

    @Override
    public void onMethodCallback() {
        retrieveBoard(model.getConnectedDeviceName());
    }

    public class GPSBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Log.d("GPSBroadcastReceiver", "Broadcast receiver");
                    if(checkLocationPermission() && checkGpsEnabled()){
                        goToFragment("fragmentBleDevice");
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
                model.setConnectedDeviceName("");
                try {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Sensor disconnected", Toast.LENGTH_SHORT).show();
                        TextView tv = findViewById(R.id.status);
                        tv.setText("Not connected");
                        tv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.red));

                        tv = findViewById(R.id.deviceNameMainActivity);
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

        TextView deviceTv = dialogView.findViewById((R.id.alert_dialog_connect_metawear_title));
        String text = "Connecting to " + model.getConnectedDeviceName() + "...";
        deviceTv.setText(text);

        Button undoBtn = dialogView.findViewById(R.id.undo_btn);
        undoBtn.setOnClickListener(v -> {
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
        Button undoBtn = dialogView.findViewById(R.id.undo_btn);

        title.setText("Connection failed");
        text.setVisibility(View.GONE);
        undoBtn.setText("Retry");

        undoBtn.setOnClickListener(v -> {
            connectDialog.dismiss();
            createNewDialog(CONNECTION_DIALOG);
            connectBoard();
        });

        return connectDialog;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) { }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) { }
}

