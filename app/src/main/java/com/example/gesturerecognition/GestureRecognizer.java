package com.example.gesturerecognition;

import java.util.ArrayList;

interface GestureEventListener {
    void onGesture(double timestamp_start, double timestamp_ending);
}

public class GestureRecognizer {
    private final double starting_value;
    private final double ending_value;
    private final double gesture_duration;
    private double timestamp_starting_value = 0;

    ArrayList<GestureEventListener> gestureEventListenerList;

    public GestureRecognizer(double starting_value, double ending_value, double gesture_duration) {
        this.starting_value = starting_value;
        this.ending_value = ending_value;
        this.gesture_duration = gesture_duration;
        this.gestureEventListenerList = new ArrayList<>();
    }

    public void addGestureEventListener(GestureEventListener gestureEventListener) {
        this.gestureEventListenerList.add(gestureEventListener);
    }

    public void recognizeGesture(double value, double epoch) {
        if(value < getStartingValue()) {
            MainActivity.starting_value_found = true;
            setTimestampStartingValue(epoch);
        }

        if(MainActivity.starting_value_found) {
            if(value >= getEndingValue()) {
                double diff = epoch - getTimestampStartingValue();

                if(diff <= getGestureDuration()) {
                    MainActivity.starting_value_found = false;
                    gestureRecognized(getTimestampStartingValue(), epoch);
                }
            }
        }
    }

    public void gestureRecognized(double timestamp_start, double timestamp_ending) {
        gestureEventListenerList.forEach(gestureEventListener -> {
            gestureEventListener.onGesture(timestamp_start, timestamp_ending);
        });

    }

    public double getStartingValue() {
        return starting_value;
    }

    public double getEndingValue() {
        return ending_value;
    }

    public double getGestureDuration() {
        return gesture_duration;
    }

    public void setTimestampStartingValue(double timestamp_starting_value) {
        this.timestamp_starting_value = timestamp_starting_value;
    }

    public double getTimestampStartingValue() {
        return timestamp_starting_value;
    }
}