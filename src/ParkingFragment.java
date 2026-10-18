package com.zai.yfac;

import android.app.Fragment;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * Adigo Smart Parking tab.
 *
 * Wraps the YF Tech CarParkingManager SDK:
 *   static CarParkingManager get()
 *   EnableState getParkingVoiceSwitchEnableState()
 *   boolean setParkingVoiceSwitchEnabled(boolean)
 *   EnableState getParkingRadarAlarmEnableState()
 *   boolean setParkingRadarAlarmEnabled(boolean)   (presumed from the get/set pair pattern)
 *   void performParkingRadarAlarmButtonClick()
 *   void registerListener(listener)
 *   void unregisterListener(listener)
 */
public class ParkingFragment extends Fragment {

    private Object mgr;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 60);
        root.setBackgroundColor(Color.parseColor("#0f3460"));

        // Header
        TextView header = new TextView(getActivity());
        header.setText("Adigo Smart Parking");
        header.setTextColor(Color.WHITE);
        header.setTextSize(20);
        header.setPadding(0, 0, 0, 40);
        root.addView(header);

        // Load CarParkingManager.get()
        mgr = YfVehicle.carParkingManager();
        if (mgr == null) {
            TextView err = new TextView(getActivity());
            err.setText("CarParkingManager not available.\n" +
                "This APK requires the device to ship the shared library\n" +
                "'com.yftech.vehicle.sdk'.\n\n" +
                "If you are running on a non-YF-Tech device, the SDK is not\n" +
                "present and parking controls will not function.");
            err.setTextColor(Color.parseColor("#ff6b6b"));
            err.setTextSize(13);
            root.addView(err);
            return root;
        }
        YfVehicle.setStatus("CarParkingManager loaded: " + mgr.getClass().getName());

        // Toggle: Parking Voice Switch
        addToggle(root, "Parking Voice Switch", "setParkingVoiceSwitchEnabled", "getParkingVoiceSwitchEnableState");

        // Toggle: Parking Radar Alarm (likely setParkingRadarAlarmEnabled / getParkingRadarAlarmEnableState)
        addToggle(root, "Parking Radar Alarm", "setParkingRadarAlarmEnabled", "getParkingRadarAlarmEnableState");

        // Button: Trigger Radar Alarm (the famous manual click)
        Button btn = new Button(getActivity());
        btn.setText("Trigger Radar Alarm Click");
        btn.setPadding(40, 40, 40, 40);
        btn.setBackgroundColor(Color.parseColor("#e94560"));
        btn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = 60;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object ok = YfVehicle.invoke(mgr, "performParkingRadarAlarmButtonClick",
                    new Class[0], new Object[0]);
                YfVehicle.setStatus("Radar alarm trigger: " + (ok == null ? "void" : ok));
                Toast.makeText(getActivity(), "Radar alarm triggered", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btn, lp);

        // Discovery helper — list all public methods on the manager (for further hacking)
        Button discover = new Button(getActivity());
        discover.setText("List SDK methods (see status bar)");
        discover.setBackgroundColor(Color.parseColor("#16213e"));
        discover.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp2.topMargin = 30;
        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (Method m : mgr.getClass().getMethods()) {
                    String n = m.getName();
                    if (n.startsWith("get") || n.startsWith("set") || n.startsWith("is") ||
                        n.startsWith("perform") || n.startsWith("register") || n.startsWith("unregister")) {
                        sb.append(m.getReturnType().getSimpleName()).append(" ").append(n).append("(");
                        Class<?>[] ps = m.getParameterTypes();
                        for (int i = 0; i < ps.length; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(ps[i].getSimpleName());
                        }
                        sb.append(")\n");
                    }
                }
                YfVehicle.setStatus(sb.toString());
            }
        });
        root.addView(discover, lp2);

        return root;
    }

    private void addToggle(LinearLayout root, String label, String setter, String getter) {
        Switch sw = new Switch(getActivity());
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setPadding(0, 30, 0, 30);

        // Try to read the current state
        boolean current = readEnableState(getter);
        sw.setChecked(current);

        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                Boolean ok = (Boolean) YfVehicle.invoke(mgr, setter,
                    new Class[]{boolean.class}, new Object[]{checked});
                YfVehicle.setStatus(label + ": " + (Boolean.TRUE.equals(ok) ? "OK" : "FAIL") + " (want=" + checked + ")");
                if (!Boolean.TRUE.equals(ok)) {
                    Toast.makeText(getActivity(), label + " set failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(sw);
    }

    private boolean readEnableState(String getterName) {
        try {
            Method m = mgr.getClass().getMethod(getterName);
            Object state = m.invoke(mgr);
            if (state == null) return false;
            String s = state.toString();
            return s.contains("ENABLED") && !s.contains("DIS");
        } catch (Throwable t) {
            // getter may not exist or may have different shape — start unchecked
            return false;
        }
    }
}
