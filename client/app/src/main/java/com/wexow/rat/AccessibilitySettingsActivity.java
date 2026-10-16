package com.wexow.rat;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AccessibilitySettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setText("System Service Settings\n\nThis service provides system-level functionality for device management.\n\nNo additional settings required.");
        tv.setTextSize(14);
        tv.setPadding(60, 60, 60, 60);
        setContentView(tv);
        
        setTitle("System Service");
    }
}
