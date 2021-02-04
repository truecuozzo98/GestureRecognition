package com.example.gesturerecognition;

import android.util.Log;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.data.Acceleration;

import java.util.ArrayList;

interface GestureEventListener {
    void onGesture(RecognizedGesture gr);
    void onLongTapStart(double timestampStart);
    void onLongTapEnd(RecognizedGesture gr);
}

public class GestureRecognizer {
    private static final double LONG_TAP_MIN_DURATION = 1;
    public final double startingValue;
    public final double endingValue;
    public final double gestureDuration;
    private final String gestureName;
    public final String axis;
    public double timestampStartingValue = 0;
    private boolean startingValueFound;

    //attributi longTap
    private double timestampLongTapStart = 0;
    private boolean startingLongTapStartFound;
    double longTapDuration = 0;

    ArrayList<GestureEventListener> gestureEventListenerList;


    //TODO: aggiungere riconoscimento nelle due direzioni
    public GestureRecognizer(String axis, String gestureName, double startingValue, double endingValue, double gestureDuration) {
        this.axis = axis;
        this.gestureName = gestureName;
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
                value = data.value(Acceleration.class).x();
                break;
            case "y":
                value = data.value(Acceleration.class).y();
                break;
            case "z":
                value = data.value(Acceleration.class).z();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + axis);
        }

        if(value < startingValue) {
            startingValueFound = true;
            timestampStartingValue = epoch;
        }

        if(startingValueFound) {
            if(value >= endingValue) {
                double diff = epoch - timestampStartingValue;

                if(diff <= gestureDuration) {
                    startingValueFound = false;
                    gestureRecognized(timestampStartingValue, epoch);

                    timestampLongTapStart = epoch;
                    startingLongTapStartFound = true;
                    longTapStartRecognized(timestampLongTapStart);
                }
            }
        }

        longTapDuration = epoch - timestampLongTapStart;
        if(startingLongTapStartFound) {
            if (value < endingValue) {
                if(longTapDuration >= LONG_TAP_MIN_DURATION) { //riconosco un longTap se è più lungo di 2 secondi
                    longTapEndRecognized(timestampLongTapStart, epoch);
                }

                startingLongTapStartFound = false;
                longTapDuration = 0;
            }
        }
    }

    public void gestureRecognized(double timestampStart, double timestampEnding) {
        RecognizedGesture rg = new RecognizedGesture(gestureName, timestampStart, timestampEnding);
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
        RecognizedGesture rg = new RecognizedGesture(gestureName, timestampStart, timestampEnding);
        for(GestureEventListener gel : gestureEventListenerList) {
            gel.onLongTapEnd(rg);
        }
    }
}