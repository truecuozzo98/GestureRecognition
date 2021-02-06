package com.example.gesturerecognition;

import android.util.Log;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;

import java.util.ArrayList;

interface GestureEventListener {
    void onGestureStarts(double timestampStart, double timestampEnding);
    void onGestureEnds(RecognizedGesture gr);
}

public class GestureRecognizer {
    public static final int SENSOR_ACCELEROMETER = 0;
    public static final int SENSOR_GYRO = 1;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    private final String gestureName;
    private final int axis;
    private final boolean increasing; //true se i dati tra la soglia di partenza e quella finale crescono (false viceversa)
    private final int sensor;
    private final double startingValue;
    private final double endingValue;

    private final double maxGestureDuration; //in millis
    private double timestampStartingValue = 0;
    private boolean startingValueFound;

    //attributi longTap
    private double gestureStartedTimestamp = 0;
    private boolean startingGestureFound;

    ArrayList<GestureEventListener> gestureEventListenerList;

    public GestureRecognizer(String gestureName, int axis, boolean increasing, int sensor, double startingValue, double endingValue, double maxGestureDuration) {
        this.gestureName = gestureName;
        this.axis = axis;
        this.increasing = increasing;
        this.sensor = sensor;
        if(increasing) {
            this.startingValue = startingValue;
            this.endingValue = endingValue;
        } else {
            this.startingValue = -1 * startingValue;
            this.endingValue = -1 * endingValue;
        }

        this.maxGestureDuration = maxGestureDuration;
        this.gestureEventListenerList = new ArrayList<>();
    }

    public void addGestureEventListener(GestureEventListener gestureEventListener) {
        this.gestureEventListenerList.add(gestureEventListener);
    }

    public void recognizeGesture(Data data, double epoch) {
        double value;

        switch (axis) {
            case AXIS_X:
                value = returnXValue(data);
                break;
            case AXIS_Y:
                value = returnYValue(data);
                break;
            case AXIS_Z:
                value = returnZValue(data);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + axis);
        }

        if(!increasing){
            value = -1 * value;
        }

        if(value < startingValue) {
            startingValueFound = true;
            timestampStartingValue = epoch;
        }

        if(startingValueFound && value >= endingValue) {
            double diff = epoch - timestampStartingValue;

            if(diff <= maxGestureDuration) {
                startingValueFound = false;
                notifyGestureStarts(timestampStartingValue, epoch);

                gestureStartedTimestamp = epoch;
                startingGestureFound = true;
            }
        }

        if(startingGestureFound && value < endingValue) {
            notifyGestureEnds(timestampStartingValue, gestureStartedTimestamp, epoch);
            startingGestureFound = false;
        }
    }

    public void notifyGestureStarts(double timestampStart, double timestampEnding) {
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onGestureStarts(timestampStart, timestampEnding);
        }
    }

    public void notifyGestureEnds(double timestampStart, double timestampEnding, double timestampEndingGesture) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, timestampStart, timestampEnding, timestampEndingGesture);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onGestureEnds(rg);
        }
    }

    private double returnXValue (Data data) {
        switch (sensor) {
            case SENSOR_ACCELEROMETER:
                return data.value(Acceleration.class).x();
            case SENSOR_GYRO:
                return data.value(AngularVelocity.class).x();
            default:
                throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private double returnYValue (Data data) {
        switch (sensor) {
            case SENSOR_ACCELEROMETER:
                return data.value(Acceleration.class).y();
            case SENSOR_GYRO:
                return data.value(AngularVelocity.class).y();
            default:
                throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private double returnZValue (Data data) {
        switch (sensor) {
            case SENSOR_ACCELEROMETER:
                return data.value(Acceleration.class).z();
            case SENSOR_GYRO:
                return data.value(AngularVelocity.class).z();
            default:
                throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    public int getSensor() {
        return sensor;
    }
}