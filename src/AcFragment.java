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
 * AC climate control tab.
 *
 * Wraps the YF Tech AirConditionManager SDK:
 *   static AirConditionManager get()
 *   EnableState getAirSelfDryingEnableState()
 *   boolean setAirSelfDryingEnabled(boolean)
 *   AirCirculationMode getAirCirculationModeOfAutoMode()
 *   boolean setAirCirculationModeOfAutoMode(AirCirculationMode)
 *   EnableState getAirUnlockVentilationEnableState()
 *   boolean setAirUnlockVentilationEnabled(boolean)
 *   ComfortCurveMode getComfortCurveMode()
 *   boolean setComfortCurveMode(ComfortCurveMode)
 *   FanSpeed getFanSpeedOfAutoMode()
 *   boolean setFanSpeedOfAutoMode(FanSpeed)
 */
public class AcFragment extends Fragment {

    private Object mgr;  // AirConditionManager instance

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 60);
        root.setBackgroundColor(Color.parseColor("#0f3460"));

        // Header
        TextView header = new TextView(getActivity());
        header.setText("AC Climate Control");
        header.setTextColor(Color.WHITE);
        header.setTextSize(20);
        header.setPadding(0, 0, 0, 40);
        root.addView(header);

        // Load AirConditionManager.get()
        mgr = YfVehicle.airConditionManager();
        if (mgr == null) {
            TextView err = new TextView(getActivity());
            err.setText("AirConditionManager not available.\n" +
                "This APK requires the device to ship the shared library\n" +
                "'com.yftech.vehicle.sdk'.\n\n" +
                "If you are running on a non-YF-Tech device, the SDK is not\n" +
                "present and AC controls will not function.");
            err.setTextColor(Color.parseColor("#ff6b6b"));
            err.setTextSize(13);
            root.addView(err);
            return root;
        }
        YfVehicle.setStatus("AirConditionManager loaded: " + mgr.getClass().getName());

        // Toggles
        addToggle(root, "AC Self-Drying", "setAirSelfDryingEnabled", "getAirSelfDryingEnableState");
        addToggle(root, "Unlock Ventilation", "setAirUnlockVentilationEnabled", "getAirUnlockVentilationEnableState");

        // Fan Speed radio
        addFanSpeedSelector(root);

        // Comfort Curve radio
        addComfortCurveSelector(root);

        // Air Circulation radio
        addCirculationModeSelector(root);

        return root;
    }

    private void addToggle(LinearLayout root, String label, String setter, String getter) {
        Switch sw = new Switch(getActivity());
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setPadding(0, 30, 0, 30);

        // Read current state — getter returns EnableState, which has toString returning ENABLED/DISABLED/...
        boolean current = readEnableState(getter);
        sw.setChecked(current);

        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                Boolean ok = (Boolean) YfVehicle.invoke(mgr, setter, new Class[]{boolean.class}, new Object[]{checked});
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
            // ENABLED -> true; DISABLED/DEFAULT/UNKNOWN -> false
            return s.contains("ENABLED") && !s.contains("DIS");
        } catch (Throwable t) {
            return false;
        }
    }

    private void addFanSpeedSelector(LinearLayout root) {
        TextView label = new TextView(getActivity());
        label.setText("Fan Speed (Auto Mode)");
        label.setTextColor(Color.parseColor("#cccccc"));
        label.setPadding(0, 50, 0, 20);
        root.addView(label);

        String[] speeds = {"LOW", "NORMAL", "HIGH"};
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String s : speeds) {
            Button b = new Button(getActivity());
            b.setText(s);
            b.setPadding(20, 20, 20, 20);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object speed = YfVehicle.fanSpeed(s);
                    if (speed == null) {
                        YfVehicle.setStatus("FanSpeed enum value '" + s + "' not found");
                        return;
                    }
                    Boolean ok = (Boolean) YfVehicle.invoke(mgr, "setFanSpeedOfAutoMode",
                        new Class[]{YfVehicle.cls("com.yftech.vehicle.internal.adapter.IAirCondition$FanSpeed")},
                        new Object[]{speed});
                    YfVehicle.setStatus("FanSpeed=" + s + " → " + (Boolean.TRUE.equals(ok) ? "OK" : "FAIL"));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(b, lp);
        }
        root.addView(row);
    }

    private void addComfortCurveSelector(LinearLayout root) {
        TextView label = new TextView(getActivity());
        label.setText("Comfort Curve Mode");
        label.setTextColor(Color.parseColor("#cccccc"));
        label.setPadding(0, 50, 0, 20);
        root.addView(label);

        String[] modes = {"SOFT", "NORMAL", "FAST"};
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String s : modes) {
            Button b = new Button(getActivity());
            b.setText(s);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object mode = YfVehicle.comfortCurveMode(s);
                    if (mode == null) {
                        YfVehicle.setStatus("ComfortCurveMode '" + s + "' not found");
                        return;
                    }
                    Boolean ok = (Boolean) YfVehicle.invoke(mgr, "setComfortCurveMode",
                        new Class[]{YfVehicle.cls("com.yftech.vehicle.internal.adapter.IAirCondition$ComfortCurveMode")},
                        new Object[]{mode});
                    YfVehicle.setStatus("ComfortCurve=" + s + " → " + (Boolean.TRUE.equals(ok) ? "OK" : "FAIL"));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(b, lp);
        }
        root.addView(row);
    }

    private void addCirculationModeSelector(LinearLayout root) {
        TextView label = new TextView(getActivity());
        label.setText("Air Circulation Mode (Auto)");
        label.setTextColor(Color.parseColor("#cccccc"));
        label.setPadding(0, 50, 0, 20);
        root.addView(label);

        // Try a few likely enum values; we don't know the exact names without the SDK
        String[] modes = {"INTERNAL", "EXTERNAL", "AUTO", "FRESH"};
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String s : modes) {
            Button b = new Button(getActivity());
            b.setText(s);
            b.setTextSize(11);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object mode = YfVehicle.airCirculationMode(s);
                    if (mode == null) {
                        YfVehicle.setStatus("AirCirculationMode '" + s + "' not found in enum");
                        return;
                    }
                    Boolean ok = (Boolean) YfVehicle.invoke(mgr, "setAirCirculationModeOfAutoMode",
                        new Class[]{YfVehicle.cls("com.yftech.vehicle.internal.adapter.IAirCondition$AirCirculationMode")},
                        new Object[]{mode});
                    YfVehicle.setStatus("Circulation=" + s + " → " + (Boolean.TRUE.equals(ok) ? "OK" : "FAIL"));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(b, lp);
        }
        root.addView(row);
    }
}
