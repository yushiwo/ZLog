package zr.com.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.zr.zlog.log.DLog;

/**
 * @author zr
 * @Date 17/1/12
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DLog.d("zr", "test");
    }
}
