package com.example.gesturerecognition;

import androidx.annotation.NonNull;

import java.math.BigDecimal;

public class RecognizedGesture {
    private String gestureName;
    private double timestampStartingValue;
    private double timestampEndingValue;
    private final double timeFromStartingToEnding;   //in seconds
    private final double gestureDuration;   //in seconds

    /*private double startingValue;
    private double endingValue;*/

    public RecognizedGesture(String gestureName, double timestampStartingValue, double timestampEndingValue, double timestampEndingGesture /*, double starting_value, double ending_value*/) {
        this.gestureName = gestureName;
        this.timestampStartingValue = timestampStartingValue;
        this.timestampEndingValue = timestampEndingValue;
        this.timeFromStartingToEnding = timestampEndingValue - timestampStartingValue;
        this.gestureDuration = timestampEndingGesture - timestampEndingValue;
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
        BigDecimal start = round((float) timestampStartingValue,2);
        BigDecimal end = round((float) timestampEndingValue,2);
        BigDecimal duration = round((float) gestureDuration,2);
        BigDecimal time = round((float) timeFromStartingToEnding,2);
        return "name: " + gestureName + ", timestamp start: " + start + "s, timestamp end: " + end + "s, time from start to end: " + time + ", gesture duration: " + duration + "s";

    }

    public BigDecimal round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }
}
