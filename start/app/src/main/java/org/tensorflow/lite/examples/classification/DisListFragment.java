package org.tensorflow.lite.examples.classification;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;


public class DisListFragment extends Fragment implements AdapterView.OnItemClickListener {
    private FragmentManager fManager;
    private ArrayList<Disease> datas;
    private ListView list_news;

    public DisListFragment(){

    }

    //根据Fragment管理器和数据进行构造
    public DisListFragment(FragmentManager fManager, ArrayList<Disease> datas) {
        this.fManager = fManager;
        this.datas = datas;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fg_diseaselist, container, false);//加载布局
        list_news = (ListView) view.findViewById(R.id.list_disease);//绑定控件
        MyAdapter myAdapter = new MyAdapter(datas, getActivity());//使用数据和活动创建Adapter
        list_news.setAdapter(myAdapter);//绑定Adapter
        list_news.setOnItemClickListener(this);//绑定控件的点击回调方法
        return view;
    }


    //当Item点击发生时
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        FragmentTransaction fTransaction = fManager.beginTransaction();
        DiseaseSolutionFragment ncFragment = new DiseaseSolutionFragment();//创建一个病害防治方法Fragment
        Bundle bd = new Bundle();
        bd.putString("content", datas.get(position).getSolution());//获得指定item的solution
        ncFragment.setArguments(bd);//将包含solution字符串的Bundle输入给Fragment
        //获取Activity的控件
        TextView txt_title = (TextView) getActivity().findViewById(R.id.txt_title);
        txt_title.setText(datas.get(position).getName());//把病害的名称付给新的顶部title
        //加上Fragment替换动画
        fTransaction.setCustomAnimations(R.anim.fragment_slide_left_enter, R.anim.fragment_slide_left_exit);
        fTransaction.replace(R.id.fl_content, ncFragment);//用新建的Fragment替代预留的布局位置
        //调用addToBackStack将Fragment添加到栈中
        fTransaction.addToBackStack(null);
        fTransaction.commit();
    }
}