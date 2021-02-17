package com.example.gesturerecognition;

import android.util.Log;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;

import java.util.ArrayList;

interface GestureEventListener {
    void onGestureStarts(String gestureName, double timestampStart, double timestampEnding);
    void onGestureEnds(RecognizedGesture rg);
}

public class GestureRecognizer {
    public static final int SENSOR_ACCELEROMETER = 0;
    public static final int SENSOR_GYRO = 1;
    public static final int SENSOR_GRAVITY = 2;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    private final String gestureName;
    private final int axis;
    private final boolean increasing; //true se i dati tra la soglia di partenza e quella finale crescono (false viceversa)
    private final int sensor;
    private final double StartingValue;
    private final double endingValue;

    private final double maxGestureDuration; //in millis
    private double timestampStartingValue = 0;
    private boolean startingValueFound;

    private double gestureStartedTimestamp = 0;
    private boolean startingGestureFound;

    //TODO: generalizzare per ogni asse
    private double currentAngle;

    ArrayList<GestureEventListener> gestureEventListenerList;
    private double previousTimestamp;
    private double currentTime;


    public GestureRecognizer(String gestureName, int axis, boolean increasing, int sensor, double StartingValue, double endingValue, double maxGestureDuration) {
        this.gestureName = gestureName;
        this.axis = axis;
        this.increasing = increasing;
        this.sensor = sensor;
        if(increasing) {
            this.StartingValue = StartingValue;
            this.endingValue = endingValue;
        } else {
            this.StartingValue = -1 * StartingValue;
            this.endingValue = -1 * endingValue;
        }

        this.maxGestureDuration = maxGestureDuration;
        this.gestureEventListenerList = new ArrayList<>();
        this.previousTimestamp = 0;
        this.currentTime = 0;

        this.currentAngle = 0;
    }

    public void addGestureEventListener(GestureEventListener gestureEventListener) {
        this.gestureEventListenerList.add(gestureEventListener);
    }

    public void recognizeGesture(Data data) {
        double epoch = data.timestamp().getTimeInMillis();
        if(!(previousTimestamp == 0)) {
            currentTime += (epoch - previousTimestamp) / 1000;
        }

        double value;
        switch (axis) {
            case AXIS_X:
                value = returnXValue(data, epoch);
                break;
            case AXIS_Y:
                value = returnYValue(data, epoch);
                break;
            case AXIS_Z:
                value = returnZValue(data, epoch);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + axis);
        }

        Log.d("value", "value: " + value);

        if(!increasing){
            value = -1 * value;
        }

        if(value < StartingValue) {
            startingValueFound = true;
            timestampStartingValue = currentTime;
        }

        if(startingValueFound && value >= endingValue) {
            double diff = currentTime - timestampStartingValue;

            if(diff <= maxGestureDuration) {
                startingValueFound = false;
                notifyGestureStarts(timestampStartingValue, currentTime);

                gestureStartedTimestamp = currentTime;
                startingGestureFound = true;
            }
        }

        if(startingGestureFound && value < endingValue) {
            notifyGestureEnds(timestampStartingValue, gestureStartedTimestamp, currentTime);
            startingGestureFound = false;
        }

        previousTimestamp = epoch;
    }


    public void notifyGestureStarts(double timestampStart, double timestampEnding) {
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onGestureStarts(gestureName, timestampStart, timestampEnding);
        }
    }

    public void notifyGestureEnds(double timestampStart, double timestampEnding, double timestampEndingGesture) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, timestampStart, timestampEnding, timestampEndingGesture);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onGestureEnds(rg);
        }
    }

    private double returnXValue(Data data, double epoch) {
        switch (sensor) {
            case SENSOR_ACCELEROMETER:
            case SENSOR_GRAVITY:
                return data.value(Acceleration.class).x();
            case SENSOR_GYRO:
                return fromGyroToAngle(data.value(AngularVelocity.class).x(), epoch);
            default:
                throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private double returnYValue(Data data, double epoch) {
        switch (sensor) {
            case SENSOR_ACCELEROMETER:
            case SENSOR_GRAVITY:
                return data.value(Acceleration.class).y();
            case SENSOR_GYRO:
                return fromGyroToAngle(data.value(AngularVelocity.class).y(), epoch);
            default:
                throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private double returnZValue(Data data, double epoch) {
        switch (sensor) {
            case SENSOR_ACCELEROMETER:
            case SENSOR_GRAVITY:
                return data.value(Acceleration.class).z();
            case SENSOR_GYRO:
                return fromGyroToAngle(data.value(AngularVelocity.class).z(), epoch);
            default:
                throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }


    private double fromGyroToAngle(double sample, double epoch) {
        //Calculate the time elapsed since the last sample by differencing the time samples
        double deltaTime = 0;
        if(previousTimestamp != 0) {
            deltaTime = (epoch - previousTimestamp) / 1000;
        }

        //Multiply the sample and the time difference - this is your change in angle since the last sample
        double deltaAngle = sample * deltaTime;

        //Add this difference to your current angle
        currentAngle += deltaAngle;

        //Save the current time as previous time
        previousTimestamp = epoch;

        return currentAngle;
    }

    public double getCurrentTime() {
        return currentTime;
    }
}