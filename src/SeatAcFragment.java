package com.zai.yfac;

import android.app.Fragment;
import android.graphics.Color;
import android.os.Bundle;
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
 * Seat AC tab.
 *
 * Wraps seat-related APIs from the YF Tech SDK.
 *
 * Confirmed API (from CarPartsManager.smali reflection of internal calls):
 *   - CarPartsManager.getSeatDriverConvenientAccessEnabled()  (private)
 *   - CarPartsManager.setSeatDriverConvenientAccessEnabled(boolean)  (private)
 *
 * These are private on CarPartsManager; the public accessor is the
 * getEnabled(Function)/setEnabled(Function, boolean) pattern on BaseManager.
 * We call those generic paths via reflection.
 *
 * In addition, we probe a few likely seat-heating / seat-ventilation methods
 * on AirConditionManager (the platform may have moved seat AC under
 * AirConditionManager or a separate CarSeatManager). The "Probe seat APIs"
 * button dumps the discoverable method names so the user can extend the app.
 */
public class SeatAcFragment extends Fragment {

    private Object carPartsMgr;
    private Object airConditionMgr;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 60);
        root.setBackgroundColor(Color.parseColor("#0f3460"));

        TextView header = new TextView(getActivity());
        header.setText("Seat AC");
        header.setTextColor(Color.WHITE);
        header.setTextSize(20);
        header.setPadding(0, 0, 0, 40);
        root.addView(header);

        // Load managers (best-effort)
        carPartsMgr = YfVehicle.carPartsManager();
        airConditionMgr = YfVehicle.airConditionManager();

        if (carPartsMgr == null && airConditionMgr == null) {
            TextView err = new TextView(getActivity());
            err.setText("Neither CarPartsManager nor AirConditionManager is available.\n" +
                "This APK requires the device to ship the shared libraries\n" +
                "'com.yftech.vehicle.sdk' and 'com.yftech.vehicle.internal.adapter'.");
            err.setTextColor(Color.parseColor("#ff6b6b"));
            err.setTextSize(13);
            root.addView(err);
            return root;
        }

        YfVehicle.setStatus("Loaded: " +
            (carPartsMgr != null ? carPartsMgr.getClass().getSimpleName() : "") +
            " + " +
            (airConditionMgr != null ? airConditionMgr.getClass().getSimpleName() : ""));

        // Toggle: Seat Driver Convenient Access (the confirmed seat API)
        if (carPartsMgr != null) {
            addSeatToggle(root, "Seat Driver Convenient Access",
                "setSeatDriverConvenientAccessEnabled",
                "getSeatDriverConvenientAccessEnabled");
        }

        // Probes for seat heating / ventilation (likely names on the platform)
        // These calls will gracefully fail with "method not found" if the platform
        // uses different names; the user can inspect the dump from the Probe button
        // below and update this Fragment's source.
        if (airConditionMgr != null) {
            addProbeButton(root, "Probe seat APIs on AirConditionManager", airConditionMgr);
        }
        if (carPartsMgr != null) {
            addProbeButton(root, "Probe seat APIs on CarPartsManager", carPartsMgr);
        }

        return root;
    }

    /** Try to invoke a no-arg boolean setter on the manager (best-effort). */
    private void addSeatToggle(LinearLayout root, String label, String setter, String getter) {
        Switch sw = new Switch(getActivity());
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setPadding(0, 30, 0, 30);
        // Try to read current value (may throw NoSuchMethodException since the methods are private)
        boolean current = readBooleanViaReflection(carPartsMgr, getter);
        sw.setChecked(current);

        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                // Try the direct setter first (even though it's private in the source smali,
                // reflection + setAccessible(true) will reach it).
                Boolean ok = invokePrivate(carPartsMgr, setter, checked);
                YfVehicle.setStatus(label + ": " + (Boolean.TRUE.equals(ok) ? "OK" : "FAIL") + " (want=" + checked + ")");
                if (!Boolean.TRUE.equals(ok)) {
                    Toast.makeText(getActivity(), label + " set failed — see SDK probe dump", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(sw);
    }

    private void addProbeButton(LinearLayout root, String label, final Object mgr) {
        Button b = new Button(getActivity());
        b.setText(label);
        b.setBackgroundColor(Color.parseColor("#16213e"));
        b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = 30;
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                sb.append("--- ").append(mgr.getClass().getName()).append(" ---\n");
                for (Method m : mgr.getClass().getDeclaredMethods()) {
                    String n = m.getName();
                    if (n.toLowerCase().contains("seat") || n.toLowerCase().contains("vent") ||
                        n.toLowerCase().contains("heat") || n.toLowerCase().contains("massage")) {
                        sb.append(m.getReturnType().getSimpleName()).append(" ").append(n).append("(");
                        Class<?>[] ps = m.getParameterTypes();
                        for (int i = 0; i < ps.length; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(ps[i].getSimpleName());
                        }
                        sb.append(")\n");
                    }
                }
                if (sb.length() < 80) sb.append("(no seat/vent/heat/massage methods found — extend the probe filter)\n");
                YfVehicle.setStatus(sb.toString());
            }
        });
        root.addView(b, lp);
    }

    private boolean readBooleanViaReflection(Object target, String getter) {
        try {
            Method m = target.getClass().getDeclaredMethod(getter);
            m.setAccessible(true);
            Object result = m.invoke(target);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    private Boolean invokePrivate(Object target, String method, boolean value) {
        try {
            Method m = target.getClass().getDeclaredMethod(method, boolean.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(target, value);
        } catch (Throwable t) {
            YfVehicle.setError("invokePrivate " + method + " failed", t);
            return false;
        }
    }
}
