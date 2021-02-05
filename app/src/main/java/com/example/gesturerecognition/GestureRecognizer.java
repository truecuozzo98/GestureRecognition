package com.example.gesturerecognition;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;

import java.util.ArrayList;

interface GestureEventListener {
    void onGesture(RecognizedGesture gr);
    void onLongTapStart(double timestampStart);
    void onLongTapEnd(RecognizedGesture gr);
}

public class GestureRecognizer {
    private static final double LONG_TAP_MIN_DURATION = 1;
    private final String gestureName;
    public final String axis;
    private final String direction;
    public final String sensor;
    public final double startingValue;
    public final double endingValue;
    public final double gestureDuration;
    public double timestampStartingValue = 0;
    private boolean startingValueFound;

    //attributi longTap
    private double timestampLongTapStart = 0;
    private boolean startingLongTapStartFound;
    double longTapDuration = 0;

    ArrayList<GestureEventListener> gestureEventListenerList;

    public GestureRecognizer(String gestureName, String axis, String direction, String sensor, double startingValue, double endingValue, double gestureDuration) {
        this.gestureName = gestureName;
        this.axis = axis;
        this.direction = direction;
        this.sensor = sensor;
        this.startingValue = startingValue;
        this.endingValue = endingValue;
        this.gestureDuration = gestureDuration;
        this.gestureEventListenerList = new ArrayList<>();
    }

    public void addGestureEventListener(GestureEventListener gestureEventListener) {
        this.gestureEventListenerList.add(gestureEventListener);
    }

    public void recognizeGesture(Data data, double epoch) {
        double value;

        switch (axis) {
            case "x":
                value = returnXValue(data);
                break;
            case "y":
                value = returnYValue(data);
                break;
            case "z":
                value = returnZValue(data);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + axis);
        }

        switch (direction) {
            case "increasing":
                if(value < startingValue) {
                    startingValueFound = true;
                    timestampStartingValue = epoch;
                }

                if(startingValueFound) {
                    if(value >= endingValue) {
                        checkGestureIsRecognized(epoch);
                    }
                }

                longTapDuration = epoch - timestampLongTapStart;
                if(startingLongTapStartFound) {
                    if (value < endingValue) {
                        checkLongTapGestureIsRecognized(epoch);
                    }
                }
                break;
            case "decreasing":
                if(value > startingValue) {
                    startingValueFound = true;
                    timestampStartingValue = epoch;
                }

                if(startingValueFound) {
                    if(value <= endingValue) {
                        checkGestureIsRecognized(epoch);
                    }
                }

                longTapDuration = epoch - timestampLongTapStart;
                if(startingLongTapStartFound) {
                    if (value > endingValue) {
                        checkLongTapGestureIsRecognized(epoch);
                    }
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + direction);
        }
    }

    public void gestureRecognized(double timestampStart, double timestampEnding) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, "single tap", timestampStart, timestampEnding);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onGesture(rg);
        }
    }

    public void longTapStartRecognized(double timestampStart) {
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onLongTapStart(timestampStart);
        }
    }

    public void longTapEndRecognized(double timestampStart, double timestampEnding) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, "long tap", timestampStart, timestampEnding);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onLongTapEnd(rg);
        }
    }

    private double returnXValue (Data data) {
        if(sensor.equals("accelerometer")) {
            return data.value(Acceleration.class).x();
        } else if (sensor.equals("gyro")) {
            return data.value(AngularVelocity.class).x();
        } else {
            throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private double returnYValue (Data data) {
        if(sensor.equals("accelerometer")) {
            return data.value(Acceleration.class).y();
        } else if (sensor.equals("gyro")) {
            return data.value(AngularVelocity.class).y();
        } else {
            throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private double returnZValue (Data data) {
        if(sensor.equals("accelerometer")) {
            return data.value(Acceleration.class).z();
        } else if (sensor.equals("gyro")) {
            return data.value(AngularVelocity.class).z();
        } else {
            throw new IllegalStateException("Unexpected value: " + sensor);
        }
    }

    private void checkGestureIsRecognized(double epoch) {
        double diff = epoch - timestampStartingValue;

        if(diff <= gestureDuration) {
            startingValueFound = false;
            gestureRecognized(timestampStartingValue, epoch);

            timestampLongTapStart = epoch;
            startingLongTapStartFound = true;
            longTapStartRecognized(timestampLongTapStart);
        }
    }

    private void checkLongTapGestureIsRecognized(double epoch) {
        if(longTapDuration >= LONG_TAP_MIN_DURATION) { //riconosco un longTap se è più lungo di 1 secondo
            longTapEndRecognized(timestampLongTapStart, epoch);
        }

        startingLongTapStartFound = false;
        longTapDuration = 0;
    }
}