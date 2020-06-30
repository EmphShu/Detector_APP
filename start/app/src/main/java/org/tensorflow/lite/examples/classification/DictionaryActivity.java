package org.tensorflow.lite.examples.classification;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class DictionaryActivity extends AppCompatActivity {

    private TextView txt_title;//标题控件
    private FrameLayout fl_content;//嵌入的Fragment
    private Context mContext;//
    private Button backbutton;
    private ArrayList<Disease> datas = null;
    private FragmentManager fManager = null;//Fragment管理器
    private long exitTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);//绑定布局文件
        mContext = DictionaryActivity.this;
        fManager = getFragmentManager();

        bindViews();//绑定

        //创建病害列表并配给内容
        datas = new ArrayList<Disease>();
        try {
            Disease data0 = new Disease("炭疽病" ,
                    "1、与非瓜类蔬菜实行轮作\n" +
                    "2、高温防治\n" +
                    "3、药剂防治：烟熏防治、粉尘防治、喷液防治\n" +
                    "4、露地黄瓜：50%灭菌威可湿性粉剂\n" +
                    "5、保护地黄瓜：8%克炭灵粉尘剂");
            datas.add(data0);
            Disease data1 = new Disease("白粉病" ,  "1、选择种植杂交品种，中农7、12、13、津春3等\n" +
                    "2、熏蒸消毒\n" +
                    "3、切忌大水漫灌，采用膜下软管滴灌、管道暗浇等降低室内湿度\n" +
                    "4、高锰酸钾防治、小苏打溶液防治");
            datas.add(data1);
            Disease data2 = new Disease("灰霉病" ,  "1、提高夜间温度，保持在15℃以上\n" +
                    "2、增加种植环境的透光性\n" +
                    "3、切忌大水漫灌，采用膜下软管滴灌、管道暗浇等降低室内湿度\n" +
                    "4、氯溴异氰脲酸喷雾处理，次日嘧霉胺悬浮剂和啶酰菌胺");
            datas.add(data2);
            Disease data3 = new Disease("霜霉病" ,  "1、选择抗病品种，津研4、津杂1等\n" +
                    "2、喷洒10%科佳浮悬浮剂\n" +
                    "3、喷洒50%烯酰吗啉WP\n" +
                    "4、喷洒40%霜灰宁悬浮剂\n" +
                    "5、点烧法，火烧过的病斑不会复发");
            datas.add(data3);

        }catch (Exception e){
            Toast.makeText(getApplicationContext(),"资源文件错误",Toast.LENGTH_LONG).show();
        }
        DisListFragment nlFragment = new DisListFragment(fManager, datas);//创建一个病害列表碎片，并将数据传入
        FragmentTransaction ft = fManager.beginTransaction();
        ft.replace(R.id.fl_content, nlFragment);//替换布局中的Fragment布局
        ft.commit();
    }


    private void bindViews() {
        txt_title = (TextView) findViewById(R.id.txt_title);//绑定控件
        fl_content = (FrameLayout) findViewById(R.id.fl_content);//绑定Fragment布局
        backbutton=(Button)findViewById(R.id.backbutton);//绑定返回按钮
        backbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DictionaryActivity.super.onBackPressed();
            }
        });
    }


    //点击回退键的处理：判断Fragment栈中是否有Fragment
    //没，双击退出程序，否则像是Toast提示
    //有，popbackstack弹出栈
    @Override
    public void onBackPressed() {
        if (fManager.getBackStackEntryCount() == 0) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Toast.makeText(getApplicationContext(), "再按一次退出防治手册",
                        Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                super.onBackPressed();
            }
        } else {
            fManager.popBackStack();
            txt_title.setText("病害种类");
        }
    }
}
