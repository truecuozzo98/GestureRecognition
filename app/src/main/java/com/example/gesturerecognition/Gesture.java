package com.example.gesturerecognition;

public class Gesture {
    private String gesture_name;
    private double timestamp_starting_value;
    private double timestamp_ending_value;
    private double starting_value;
    private double ending_value;

    public Gesture(String gesture_name, double timestamp_starting_value, double timestamp_ending_value, double starting_value, double ending_value) {
        this.gesture_name = gesture_name;
        this.timestamp_starting_value = timestamp_starting_value;
        this.timestamp_ending_value = timestamp_ending_value;
        this.starting_value = starting_value;
        this.ending_value = ending_value;
    }

    public String getGesture_name() {
        return gesture_name;
    }

    public void setGesture_name(String gesture_name) {
        this.gesture_name = gesture_name;
    }

    public double getTimestamp_starting_value() {
        return timestamp_starting_value;
    }

    public void setTimestamp_starting_value(double timestamp_starting_value) {
        this.timestamp_starting_value = timestamp_starting_value;
    }

    public double getTimestamp_ending_value() {
        return timestamp_ending_value;
    }

    public void setTimestamp_ending_value(double timestamp_ending_value) {
        this.timestamp_ending_value = timestamp_ending_value;
    }

    public double getStarting_value() {
        return starting_value;
    }

    public void setStarting_value(double starting_value) {
        this.starting_value = starting_value;
    }

    public double getEnding_value() {
        return ending_value;
    }

    public void setEnding_value(double ending_value) {
        this.ending_value = ending_value;
    }
}
