package com.cloudalbum.publisher.android.activate;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cloudalbum.publisher.android.player.PlayerActivity;

public class ActivateActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, PlayerActivity.class));
        finish();
    }
}
