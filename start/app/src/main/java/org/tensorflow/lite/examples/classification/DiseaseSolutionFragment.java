package org.tensorflow.lite.examples.classification;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DiseaseSolutionFragment extends Fragment {

    DiseaseSolutionFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fg_solution, container, false);
        TextView txt_solution = (TextView) view.findViewById(R.id.txt_solution);
        //getArgument获取传递过来的Bundle对象
        txt_solution.setText(getArguments().getString("content"));//将获取的字符串赋给solution textview控件
        return view;
    }

}