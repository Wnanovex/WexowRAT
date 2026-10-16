package com.wexow.rat;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SetupCompleteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_complete);

        Button btn = findViewById(R.id.btnClose);
        btn.setOnClickListener(v -> finishAffinity());
    }
}