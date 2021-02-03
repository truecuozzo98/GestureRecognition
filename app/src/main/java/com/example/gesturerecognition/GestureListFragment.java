package com.example.gesturerecognition;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class GestureListFragment extends Fragment {
    public GestureListFragment() { /*Required empty public constructor*/ }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    //TODO: non passare allGestureList come statico
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gesture_list, container, false);
        Button back = view.findViewById(R.id.back);
        back.setOnClickListener(v -> removeFragment("gestureListFragment"));

        String nl = System.getProperty("line.separator");
        TextView gestureListTv = view.findViewById(R.id.gesture_list_tv);
        StringBuilder s = new StringBuilder();

        if(MainActivity.allGestureList.isEmpty()){
            gestureListTv.setText("No gestures are registered yet");
        } else {
            gestureListTv.setText("");

            for(JSONObject x : MainActivity.allGestureList) {
                try {
                    s.append(x.getString("date")).append(nl);

                    ArrayList<RecognizedGesture> arrayListRecognizedGesture = (ArrayList<RecognizedGesture>) x.get("gestureList");
                    for(RecognizedGesture g : arrayListRecognizedGesture) {
                        s.append(g.toStringRoundedDecimal()).append(nl);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                gestureListTv.setText(s.append(nl).append(nl));
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