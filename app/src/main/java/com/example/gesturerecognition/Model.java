package com.example.gesturerecognition;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.json.JSONObject;
import java.util.ArrayList;

public class Model {
    private static final Model ourInstance = new Model();
    public ArrayList<JSONObject> allGestureList;
    public String connectedDeviceName = null;

    public static Model getInstance() {
        return ourInstance;
    }

    private Model() {
        allGestureList = new ArrayList<>();
    }

    public void addGestureSession(JSONObject obj) {
        allGestureList.add(obj);
    }

    public boolean isAllGestureListEmpty() {
        return allGestureList.isEmpty();
    }

    public ArrayList<JSONObject> getAllGestureList() {
        return allGestureList;
    }

    public void setConnectedDeviceName(String connectedDeviceName) {
        this.connectedDeviceName = connectedDeviceName;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }
}
