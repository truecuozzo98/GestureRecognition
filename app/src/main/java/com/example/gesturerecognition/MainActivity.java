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
import android.widget.LinearLayout;
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

import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
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

    public ArrayList<RecognizedGesture> recognizedGestureList = new ArrayList<>();

    private final GPSBroadcastReceiver gpsBroadcastReceiver = new GPSBroadcastReceiver();

    private EasyCsv easyCsv;

    private AlertDialog connectDialog;
    
    private final Model model = Model.getInstance();
    private final ArrayList<GestureRecognizer> gestureRecognizerList = new ArrayList<>();

    //private GestureRecognizer gestureRecognizer;
    private SensorFusionBosch sensorFusion;

    TextView tvGestureStatus;
    TextView tvGestureCounter;
    int sensor;

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

        /*Button led = findViewById(R.id.buttonLed);
        led.setOnClickListener(v -> blinkLed());*/

        Button start = findViewById(R.id.buttonStart);
        start.setOnClickListener(v -> startGettingData());

        Button stop = findViewById(R.id.buttonStop);
        stop.setOnClickListener(v -> stopGettingData());

        Button gestureListButton = findViewById(R.id.gestureListButton);
        gestureListButton.setOnClickListener(v -> goToFragment("gestureListFragment"));

        tvGestureStatus = findViewById(R.id.gestureStatus);
        tvGestureCounter = findViewById(R.id.counterGestures);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        registerReceiver(gpsBroadcastReceiver, new IntentFilter("android.location.PROVIDERS_CHANGED"));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        easyCsv = new EasyCsv(this);
        easyCsv.setSeparatorColumn(",");
        easyCsv.setSeperatorLine(";");

        ArrayList<String> arrayList = loadSpinnerGestureNames();

        Spinner spinner = findViewById(R.id.gesture_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        for(String x : arrayList) {
            spinnerAdapter.add(x);
        }
        spinnerAdapter.notifyDataSetChanged();
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

        tvGestureCounter.setText(String.valueOf(0));

        recognizedGestureList.clear();
        Model.dataListString.clear();
        gestureRecognizerList.clear();

        Spinner spinner = findViewById(R.id.gesture_spinner);
        String text = spinner.getSelectedItem().toString();

        initGestureRecognizer(text);

        if(sensor == GestureRecognizer.SENSOR_ACCELEROMETER) {
            getAccelerometerData();
        }

        if(sensor == GestureRecognizer.SENSOR_GRAVITY) {
            getGravityData();
        }

        if(sensor == GestureRecognizer.SENSOR_GYRO) {
            getGyroData();
        }


        Toast.makeText(MainActivity.this, "Started", Toast.LENGTH_SHORT).show();
    }

    private void initGestureRecognizer(String text) {
        try {
            JSONObject jsonObject = new JSONObject(loadJSONGestureParameters());
            JSONArray jsonArray = jsonObject.getJSONArray("gestures");

            for (int i = 0; i < jsonArray.length() ; i++) {
                String gestureName = jsonArray.getJSONObject(i).getString("name");
                if(text.toLowerCase().equals(gestureName.toLowerCase())) {
                    JSONArray actionsJSON = jsonArray.getJSONObject(i).getJSONArray("actions");
                    for(int j = 0 ; j < actionsJSON.length() ; j++ ) {
                        String actionName = actionsJSON.getJSONObject(j).getString("name");
                        int axis = actionsJSON.getJSONObject(j).getInt("axis");
                        boolean increasing = actionsJSON.getJSONObject(j).getBoolean("increasing");
                        sensor = actionsJSON.getJSONObject(j).getInt("sensor");
                        double startingValue = actionsJSON.getJSONObject(j).getDouble("startingValue");
                        double endingValue = actionsJSON.getJSONObject(j).getDouble("endingValue");
                        double gestureDuration = actionsJSON.getJSONObject(j).getDouble("maxGestureDuration");

                        GestureRecognizer gr = new GestureRecognizer(actionName, axis, increasing, sensor, startingValue, endingValue, gestureDuration);

                        gr.addGestureEventListener(new GestureEventListener() {
                            @Override
                            public void onGestureStarts(String gestureName, double timestampStart, double timestampEnding) {
                                Log.d("onGestureStarts", "onGestureStarts, gestureName: " + gestureName + ", timestampStart: " + timestampStart + ", timestampEnding: " + timestampEnding);
                                int counter = Integer.parseInt(tvGestureCounter.getText().toString()) +1;

                                runOnUiThread(() -> {
                                    tvGestureCounter.setText(String.valueOf(counter));

                                    switch (gestureName) {
                                        case "wrist2":
                                        case "arm3_2":
                                            LinearLayout ll = findViewById(R.id.rightArea);
                                            ll.setBackgroundResource(R.color.emerald);
                                            break;
                                        default:
                                            ll = findViewById(R.id.leftArea);
                                            ll.setBackgroundResource(R.color.emerald);
                                    }
                                });
                            }

                            @Override
                            public void onGestureEnds(RecognizedGesture rg) {
                                Log.d("onGestureEnds", "onGestureEnds, rg:" + rg.toStringRoundedDecimal());
                                recognizedGestureList.add(rg);
                                runOnUiThread(() -> {
                                    switch (rg.getGestureName()) {
                                        case "wrist2":
                                        case "arm3_2":
                                            LinearLayout ll = findViewById(R.id.rightArea);
                                            ll.setBackgroundResource(R.color.red);
                                        default:
                                            ll = findViewById(R.id.leftArea);
                                            ll.setBackgroundResource(R.color.red);
                                    }
                                });
                            }
                        });
                        gestureRecognizerList.add(gr);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void getAccelerometerData() {
        accelerometer = board.getModule(Accelerometer.class);
        accelerometer.configure()
                .odr(5f)
                .range(4f)
                .commit();
        accelerometer.start();

        accelerometer.acceleration().addRouteAsync(source -> source.stream((Subscriber) (data, env) -> {

            for(GestureRecognizer gr : gestureRecognizerList) {
                gr.recognizeGesture(data);
            }

            long epoch = data.timestamp().getTimeInMillis();
            double timestamp = gestureRecognizerList.get(0).getCurrentTime();                           //gestureRecognizer.getCurrentTime();
            double x = data.value(Acceleration.class).x();
            double y = data.value(Acceleration.class).y();
            double z = data.value(Acceleration.class).z();
            Model.dataListString.add(epoch + "," + timestamp + "," + x + "," + y + "," + z + ";");
        })).continueWith((Continuation<Route, Void>) task -> {
            accelerometer.acceleration().start();
            accelerometer.start();
            return null;
        });
    }

    private void getGravityData() {
        sensorFusion = board.getModule(SensorFusionBosch.class);
        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_16G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_2000DPS)
                .commit();
        sensorFusion.start();

        sensorFusion.gravity().addRouteAsync(source -> source.stream((Subscriber) (data, env) -> {
            for(GestureRecognizer gr : gestureRecognizerList) {
                gr.recognizeGesture(data);
            }
            long epoch = data.timestamp().getTimeInMillis();
            double timestamp = gestureRecognizerList.get(0).getCurrentTime();                           //gestureRecognizer.getCurrentTime();
            double x = data.value(Acceleration.class).x();
            double y = data.value(Acceleration.class).y();
            double z = data.value(Acceleration.class).z();
            Model.dataListString.add(epoch + "," + timestamp + "," + x + "," + y + "," + z + ";");
        })).continueWith((Continuation<Route, Void>) task -> {
            sensorFusion.gravity().start();
            sensorFusion.start();
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
            for(GestureRecognizer gr : gestureRecognizerList) {
                gr.recognizeGesture(data);
            }
            long epoch = data.timestamp().getTimeInMillis();
            double timestamp = gestureRecognizerList.get(0).getCurrentTime();                           //gestureRecognizer.getCurrentTime();
            double x = data.value(AngularVelocity.class).x();
            double y = data.value(AngularVelocity.class).y();
            double z = data.value(AngularVelocity.class).z();
            Model.dataListString.add(epoch + "," + timestamp + "," + x + "," + y + "," + z + ";");
        })).continueWith((Continuation<Route, Void>) task -> {
            gyro.angularVelocity().start();
            gyro.start();
            return null;
        });
    }

    public void stopGettingData() {
        if(board == null) { return; }

        tvGestureStatus.setText("");

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

        if(sensorFusion != null) {
            sensorFusion.stop();
        }

        Toast.makeText(MainActivity.this, "Stopped", Toast.LENGTH_SHORT).show();

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            writeDataOnDevice();
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

    public ArrayList<String> loadSpinnerGestureNames() {
        ArrayList<String> arrayList = new ArrayList<>();

        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(loadJSONGestureParameters());
            JSONArray jsonArray = jsonObject.getJSONArray("gestures");
            for (int i = 0; i < jsonArray.length() ; i++) {
                arrayList.add(jsonArray.getJSONObject(i).getString("name"));

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return arrayList;
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
        headerList.add("Epoch,Timestamp,x-axis,y-axis,z-axis;");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        final String currentDateTime = sdf.format(new Date());
        if(!Model.dataListString.isEmpty()) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + File.separator + "GestureRecognition"), "Measuraments");
            if(!dir.exists()) {
                dir.mkdirs();
            }

            final String fileNameWithPath = PATH_DIR + "Measuraments/registration_" + currentDateTime;
            List<String> list = new ArrayList<>(Model.dataListString);
            easyCsv.createCsvFile(fileNameWithPath, headerList, list, STORAGE_REQUEST_CODE, new FileCallback() {
                @Override
                public void onSuccess(File file) {
                    Log.d("EasyCsv", "file salvato: " + file.getName());
                    Toast.makeText(MainActivity.this, "Your file has been saved in folder " + fileNameWithPath, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFail(String err) {
                    Log.d("EasyCsv", "file error: " + err);
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

