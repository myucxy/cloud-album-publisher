package com.cloudalbum.publisher.android.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cloudalbum.publisher.android.BuildConfig;
import com.cloudalbum.publisher.android.R;

public class AboutActivity extends AppCompatActivity {

    private static final String GITHUB_URL = "https://github.com/myucxy/cloud-album-publisher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        initViews();
    }

    private void initViews() {
        // 返回按钮
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // 版本信息
        TextView versionNameText = findViewById(R.id.versionNameText);
        TextView versionCodeText = findViewById(R.id.versionCodeText);

        String versionName = BuildConfig.VERSION_NAME;
        int versionCode = BuildConfig.VERSION_CODE;
        versionNameText.setText(getString(R.string.about_version_format, versionName));
        versionCodeText.setText(getString(R.string.about_version_code_format, versionCode));

        // GitHub 按钮
        Button openGithubButton = findViewById(R.id.openGithubButton);
        openGithubButton.setOnClickListener(v -> openGithubPage());
    }

    private void openGithubPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }
}