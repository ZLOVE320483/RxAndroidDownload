package com.zlove.rxandroiddownload;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.zlove.rxandroiddownload.practice.BasicDownloadActivity;
import com.zlove.rxandroiddownload.practice.market.AppMarketActivity;
import com.zlove.rxandroiddownload.practice.service.ServiceDownloadActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    private String weixin = "http://dldir1.qq.com/weixin/android/weixin6327android880.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);
    }

    @OnClick({R.id.basic_download, R.id.service_download, R.id.app_market})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.basic_download:
                startActivity(new Intent(this, BasicDownloadActivity.class));
                break;

            case R.id.service_download:
                startActivity(new Intent(this, ServiceDownloadActivity.class));
                break;

            case R.id.app_market:
                startActivity(new Intent(this, AppMarketActivity.class));
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_check_update) {
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("更新")
                    .setMessage("有新版本发布")
                    .setPositiveButton("升级", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).create();
            dialog.show();
        }
        return true;
    }
}
