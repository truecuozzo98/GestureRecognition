package com.example.gesturerecognition;

import android.util.Log;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;

import java.util.ArrayList;

interface GestureEventListener {
    void onFirstGestureStarts(double timestampStart, double timestampEnding);
    void onFirstGestureEnds(RecognizedGesture rg);
    void onSecondGestureStarts(double timestampStart, double timestampEnding);
    void onSecondGestureEnds(RecognizedGesture rg);
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
    private final double firstStartingValue;
    private final double firstEndingValue;

    private final double maxGestureDuration; //in millis
    private double timestampFirstStartingValue = 0;
    private boolean firstStartingValueFound;

    private double firstGestureStartedTimestamp = 0;
    private boolean startingFirstGestureFound;

    //TODO: generalizzare per ogni asse
    private double currentAngle;

    ArrayList<GestureEventListener> gestureEventListenerList;
    private double previousTimestamp;
    private double currentTime;
    
    //second gesture
    private final Double secondStartingValue;
    private final Double secondEndingValue;
    private boolean secondStartingValueFound;
    private double timestampSecondStartingValue;
    private double secondGestureStartedTimestamp;
    private boolean startingSecondGestureFound;

    public GestureRecognizer(String gestureName, int axis, boolean increasing, int sensor, double firstStartingValue, double firstEndingValue, double maxGestureDuration) {
        this.gestureName = gestureName;
        this.axis = axis;
        this.increasing = increasing;
        this.sensor = sensor;
        if(increasing) {
            this.firstStartingValue = firstStartingValue;
            this.firstEndingValue = firstEndingValue;
        } else {
            this.firstStartingValue = -1 * firstStartingValue;
            this.firstEndingValue = -1 * firstEndingValue;
        }

        this.maxGestureDuration = maxGestureDuration;
        this.gestureEventListenerList = new ArrayList<>();
        this.previousTimestamp = 0;
        this.currentTime = 0;

        this.currentAngle = 0;

        secondStartingValue = null;
        secondEndingValue = null;
    }

    public GestureRecognizer(String gestureName, int axis, boolean increasing, int sensor, double firstStartingValue, double firstEndingValue, double secondStartingValue, double secondEndingValue, double maxGestureDuration) {
        this.gestureName = gestureName;
        this.axis = axis;
        this.increasing = increasing;
        this.sensor = sensor;
        if(increasing) {
            this.firstStartingValue = firstStartingValue;
            this.firstEndingValue = firstEndingValue;
            this.secondStartingValue = -1 * secondStartingValue;
            this.secondEndingValue = -1 * secondEndingValue;
        } else {
            this.firstStartingValue = -1 * firstStartingValue;
            this.firstEndingValue = -1 * firstEndingValue;
            this.secondStartingValue = secondStartingValue;
            this.secondEndingValue = secondEndingValue;
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

        checkFirstGesture(value);

        if(secondStartingValue != null) {
            checkSecondGesture(value);
        }

        previousTimestamp = epoch;
    }

    private void checkFirstGesture(double value) {
        if(!increasing){
            value = -1 * value;
        }

        if(value < firstStartingValue) {
            firstStartingValueFound = true;
            timestampFirstStartingValue = currentTime;
        }

        if(firstStartingValueFound && value >= firstEndingValue) {
            double diff = currentTime - timestampFirstStartingValue;

            if(diff <= maxGestureDuration) {
                firstStartingValueFound = false;
                notifyFirstGestureStarts(timestampFirstStartingValue, currentTime);

                firstGestureStartedTimestamp = currentTime;
                startingFirstGestureFound = true;
            }
        }

        if(startingFirstGestureFound && value < firstEndingValue) {
            notifyFirstGestureEnds(timestampFirstStartingValue, firstGestureStartedTimestamp, currentTime);
            startingFirstGestureFound = false;
        }
    }

    private void checkSecondGesture(double value) {
        if(increasing){
            value = -1 * value;
        }

        if(value < secondStartingValue) {
            secondStartingValueFound = true;
            timestampSecondStartingValue = currentTime;
        }

        if(secondStartingValueFound && value >= secondEndingValue) {
            double diff = currentTime - timestampSecondStartingValue;

            if(diff <= maxGestureDuration) {
                secondStartingValueFound = false;
                notifySecondGestureStarts(timestampSecondStartingValue, currentTime);

                secondGestureStartedTimestamp = currentTime;
                startingSecondGestureFound = true;
            }
        }

        if(startingSecondGestureFound && value < secondEndingValue) {
            notifySecondGestureEnds(timestampSecondStartingValue, secondGestureStartedTimestamp, currentTime);
            startingSecondGestureFound = false;
        }
    }

    public void notifyFirstGestureStarts(double timestampStart, double timestampEnding) {
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onFirstGestureStarts(timestampStart, timestampEnding);
        }
    }

    public void notifyFirstGestureEnds(double timestampStart, double timestampEnding, double timestampEndingGesture) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, timestampStart, timestampEnding, timestampEndingGesture);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onFirstGestureEnds(rg);
        }
    }

    public void notifySecondGestureStarts(double timestampStart, double timestampEnding) {
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onSecondGestureStarts(timestampStart, timestampEnding);
        }
    }

    public void notifySecondGestureEnds(double timestampStart, double timestampEnding, double timestampEndingGesture) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, timestampStart, timestampEnding, timestampEndingGesture);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onSecondGestureEnds(rg);
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

    public int getSensor() {
        return sensor;
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