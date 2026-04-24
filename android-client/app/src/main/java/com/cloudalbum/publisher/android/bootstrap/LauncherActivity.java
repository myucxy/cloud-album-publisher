package com.cloudalbum.publisher.android.bootstrap;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;
import com.cloudalbum.publisher.android.player.PlayerActivity;
import com.cloudalbum.publisher.android.setup.SetupActivity;

public class LauncherActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceSessionRepository repository = new DeviceSessionRepository(this);
        Class<?> target = repository.isActivated() ? PlayerActivity.class : SetupActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
