package com.example.gesturerecognition;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.android.BtleService;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private BtleService.LocalBinder serviceBinder;
    private final String MW_MAC_ADDRESS= "CD:49:78:BF:7C:89";
    //private final String MW_MAC_ADDRESS= "EC:2C:09:81:22:AC";

    private MetaWearBoard board;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("board", "onDestroy");

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        Log.d("board", "onServiceConnected");
        serviceBinder = (BtleService.LocalBinder) service;

        retrieveBoard();

        Button button = (Button) findViewById(R.id.buttonConnect);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connectTo();
            }
        });

        board.onUnexpectedDisconnect(new MetaWearBoard.UnexpectedDisconnectHandler() {
            @Override
            public void disconnected(int status) {
                Log.i("MainActivity", "Unexpectedly lost connection: " + status);
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d("board", "onServiceDisconnected");

        board.disconnectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                Log.i("MainActivity", "Disconnected");
                return null;
            }
        });
    }


    public void retrieveBoard() {
        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);
    }

    public void connectTo(){
        board.connectAsync().continueWith(new Continuation<Void, Void>() {
            @Override
            public Void then(Task<Void> task) throws Exception {
                if (task.isFaulted()) {
                    Log.d("board", "Failed to connect");
                } else {
                    Log.d("board", "Connected");
                    Log.i("board", "board model = " + board.getModel());
                }
                return null;
            }
        });

        board.readDeviceInformationAsync()
                .continueWith(new Continuation<DeviceInformation, Void>() {
                    @Override
                    public Void then(Task<DeviceInformation> task) throws Exception {
                        Log.i("board", "Device Information: " + task.getResult());
                        return null;
                    }
                });
    }
}