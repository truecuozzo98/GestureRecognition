package com.example.gesturerecognition;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import com.opencsv.CSVWriter;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "CD:49:78:BF:7C:89";
    //private final String MW_MAC_ADDRESS= "EC:2C:09:81:22:AC";

    private MetaWearBoard board;
    private Accelerometer accelerometer;
    private ArrayList<JSONObject> dataSensor = new ArrayList<JSONObject>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

        Button connect = (Button) findViewById(R.id.buttonConnect);
        connect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connectTo();
            }
        });

        Button led = (Button) findViewById(R.id.buttonLed);
        led.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                blinkLedTenTimes();
            }
        });

        Button start = (Button) findViewById(R.id.buttonStart);
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startAccMeasurement();
            }
        });

        Button stop = (Button) findViewById(R.id.buttonStop);
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopAccMeasurement();
            }
        });
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        Log.d("board", "onServiceConnected");
        serviceBinder = (BtleService.LocalBinder) service;

        retrieveBoard();

        board.onUnexpectedDisconnect(new MetaWearBoard.UnexpectedDisconnectHandler() {
            @Override
            public void disconnected(int status) {
                Log.i("board", "Unexpectedly lost connection: " + status);
            }
        });
    }

    public void retrieveBoard() {
        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);
    }

    public void blinkLedTenTimes() {
        if (!board.isConnected()){
            Toast.makeText(MainActivity.this, "Please connect your sensor first", Toast.LENGTH_LONG).show();
            return;
        }

        Led led;
        if ((led = board.getModule(Led.class)) != null) {
            led.editPattern(Led.Color.BLUE, Led.PatternPreset.BLINK)
                    .repeatCount((byte) 10)
                    .commit();
            led.play();
        }
    }

    public void connectTo(){
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

        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            (new Handler()).postDelayed(this::writeDataOnDevice, 200);
        } else {
            (new Handler()).postDelayed(this::writeDataOnDevice, 200);
        }




        /*File file = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
        try {
            FileWriter outputfile = new FileWriter(file);
            CSVWriter writer = new CSVWriter(outputfile);

            List<String[]> csvData = new ArrayList<String[]>();
            csvData.add(new String[] {"timestamp", "x-axis", "y-axis", "z-axis" });

            for(JSONObject x : dataSensor){
                Log.d("board", x.toString());
                csvData.add(new String[] {(String) x.get("timestamp"), (String) x.get("x"), (String) x.get("y"), (String) x.get("z")});
            }

            writer.writeAll(csvData);
            writer.close();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }*/
    }

    public void writeDataOnDevice() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download", "MyAppDataSensor");
        if(!dir.exists()){
            dir.mkdir();
        }

        try {
            File gpxfile = new File(dir, "Data Sensor");
            FileWriter writer = new FileWriter(gpxfile);

            writer.append("timestamp, x-axis, y-axis, z-axis\n");
            
            for(JSONObject a : dataSensor){
                //Log.d("board", a.toString());
                String timestamp = (String) a.get("timestamp") + ", ";
                String x = (String) a.get("x") + ", ";
                String y = (String) a.get("y") + ", ";
                String z = (String) a.get("z") + "\n";
                writer.append((timestamp + x + y + z));
            }
            writer.flush();
            writer.close();

            Toast.makeText(MainActivity.this, "Your file ha been saved in folder " + dir, Toast.LENGTH_LONG).show();

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
}