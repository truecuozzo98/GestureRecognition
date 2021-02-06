package com.example.gesturerecognition;

import android.util.JsonReader;

import org.json.JSONObject;

import java.util.ArrayList;

public class Model {
    private static final Model ourInstance = new Model();
    public static ArrayList<JSONObject> allGestureList;
    public static String connectedDeviceName = null;

    public static Model getInstance() {
        return ourInstance;
    }

    private Model() {
        allGestureList = new ArrayList<>();
    }

    public void addGesture(JSONObject obj) {
        allGestureList.add(obj);
    }

    public boolean isAllGestureListEmpty() {
        return allGestureList.isEmpty();
    }

    public ArrayList<JSONObject> getAllGestureList() {
        return allGestureList;
    }

    public void setConnectedDeviceName(String connectedDeviceName) {
        Model.connectedDeviceName = connectedDeviceName;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }
}
