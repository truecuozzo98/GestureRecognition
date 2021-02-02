package com.example.gesturerecognition;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;

public class GestureListFragment extends Fragment {
    public GestureListFragment() { /*Required empty public constructor*/ }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gesture_list, container, false);
        Button back = view.findViewById(R.id.back);
        back.setOnClickListener(v -> removeFragment("gestureListFragment"));

        String nl = System.getProperty("line.separator");
        TextView gesture_list_tv = view.findViewById(R.id.gesture_list_tv);
        StringBuilder s = new StringBuilder();

        if(MainActivity.all_gesture_list.isEmpty()){
            gesture_list_tv.setText("No gestures are registered yet");
        } else {
            gesture_list_tv.setText("");

            for(JSONObject x : MainActivity.all_gesture_list ) {
                try {
                    s.append(x.getString("date")).append(nl);

                    ArrayList<Gesture> arrayListGesture = (ArrayList<Gesture>) x.get("gesture_list");
                    for(Gesture g : arrayListGesture) {
                        s.append(g.toStringRoundedDecimal()).append(nl);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                gesture_list_tv.setText(s.append(nl).append(nl));
            }
        }

        return view;
    }

    public void removeFragment(String tag) {
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.remove(fragment).commit();
    }


}