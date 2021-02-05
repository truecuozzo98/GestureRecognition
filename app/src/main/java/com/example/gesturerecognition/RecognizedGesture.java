package com.example.gesturerecognition;

import androidx.annotation.NonNull;

import java.math.BigDecimal;

public class RecognizedGesture {
    private String gestureName;
    private String type;

    private double timestampStartingValue;
    private double timestampEndingValue;
    private final double gestureDuration;

    /*private double startingValue;
    private double endingValue;*/

    public RecognizedGesture(String gestureName, String type, double timestampStartingValue, double timestampEndingValue /*, double starting_value, double ending_value*/) {
        this.gestureName = gestureName;
        this.type = type;
        this.timestampStartingValue = timestampStartingValue;
        this.timestampEndingValue = timestampEndingValue;
        this.gestureDuration = timestampEndingValue - timestampStartingValue;
        /*this.starting_value = starting_value;
        this.ending_value = ending_value;*/
    }

    public String getGestureName() {
        return gestureName;
    }

    public void setGestureName(String gestureName) {
        this.gestureName = gestureName;
    }

    public double getTimestampStartingValue() {
        return timestampStartingValue;
    }

    public void setTimestampStartingValue(double timestampStartingValue) {
        this.timestampStartingValue = timestampStartingValue;
    }

    public double getTimestampEndingValue() {
        return timestampEndingValue;
    }

    public void setTimestampEndingValue(double timestampEndingValue) {
        this.timestampEndingValue = timestampEndingValue;
    }

    public double getGestureDuration() {
        return gestureDuration;
    }

    /*public double getStartingValue() {
        return startingValue;
    }

    public void setStartingValue(double startingValue) {
        this.startingValue = startingValue;
    }

    public double getEndingValue() {
        return endingValue;
    }

    public void setEndingValue(double endingValue) {
        this.endingValue = endingValue;
    }*/

    @NonNull
    @Override
    public String toString() {
        return "name: " + gestureName + ", timestamp start: " + timestampStartingValue + ", timestamp end: " + timestampEndingValue;
    }

    public String toStringRoundedDecimal() {
        BigDecimal start, end, duration;
        start = round((float) timestampStartingValue,2);
        end = round((float) timestampEndingValue,2);
        duration = round((float) gestureDuration,2);
        return "name: " + gestureName + ", type: " + type + ", timestamp start: " + start + "s, timestamp end: " + end + "s, gesture duration: " + duration + "s";

    }

    public BigDecimal round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }
}
