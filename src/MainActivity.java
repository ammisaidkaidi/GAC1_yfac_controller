package com.zai.yfac;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Main entry of the YF AC Controller.
 *
 * Three tabs:
 *   1) AC      — climate control via com.yftech.vehicle.sdk.AirConditionManager
 *   2) Parking — Adigo smart parking via com.yftech.vehicle.sdk.CarParkingManager
 *   3) Seat AC — seat driver convenient access (and future seat heating/ventilation hooks)
 *
 * All vehicle SDK calls go through the YfVehicle reflection wrapper so the APK
 * has no compile-time dependency on com.yftech.* classes — those classes are
 * provided at runtime by the device's shared libraries
 * (com.yftech.vehicle.sdk, com.yftech.vehicle.internal.adapter).
 *
 * The app installs as a SEPARATE app (package com.zai.yfac), does not declare
 * android:sharedUserId, and works on any device that ships the YF Tech
 * shared libraries — no platform signing required for installation.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private static final int TAB_AC = 0;
    private static final int TAB_PARKING = 1;
    private static final int TAB_SEAT = 2;

    private TextView tabAc, tabParking, tabSeat;
    private TextView activeTab;
    private int currentTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));

        // Title bar
        TextView title = new TextView(this);
        title.setText("YF AC Controller");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setPadding(40, 50, 40, 50);
        title.setGravity(Gravity.CENTER);
        root.addView(title, linParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        // Tabs row
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(Color.parseColor("#16213e"));
        tabs.setPadding(0, 20, 0, 20);
        tabAc = makeTab("AC");
        tabParking = makeTab("Parking");
        tabSeat = makeTab("Seat AC");
        tabs.addView(tabAc, linParams(1, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(tabParking, linParams(1, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(tabSeat, linParams(1, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(tabs, linParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        // Status line (for SDK errors)
        YfVehicle.statusView = new TextView(this);
        YfVehicle.statusView.setTextColor(Color.parseColor("#aaaaaa"));
        YfVehicle.statusView.setPadding(40, 30, 40, 30);
        YfVehicle.statusView.setTextSize(12);
        root.addView(YfVehicle.statusView, linParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        // Fragment container
        LinearLayout container = new LinearLayout(this);
        container.setId(0x7fff0001);
        container.setOrientation(LinearLayout.VERTICAL);
        root.addView(container, linParams(0, 0, 1));

        setContentView(root);

        // Initial tab
        showTab(TAB_AC);
    }

    private TextView makeTab(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.LTGRAY);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(20, 30, 20, 30);
        tv.setOnClickListener(this);
        return tv;
    }

    private LinearLayout.LayoutParams linParams(int width, int height, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.weight = weight;
        return lp;
    }

    @Override
    public void onClick(View v) {
        if (v == tabAc) showTab(TAB_AC);
        else if (v == tabParking) showTab(TAB_PARKING);
        else if (v == tabSeat) showTab(TAB_SEAT);
    }

    private void showTab(int which) {
        if (currentTab == which) return;
        currentTab = which;

        // Update tab styles
        setTabStyle(tabAc, which == TAB_AC);
        setTabStyle(tabParking, which == TAB_PARKING);
        setTabStyle(tabSeat, which == TAB_SEAT);

        Fragment frag;
        switch (which) {
            case TAB_PARKING:
                frag = new ParkingFragment();
                break;
            case TAB_SEAT:
                frag = new SeatAcFragment();
                break;
            case TAB_AC:
            default:
                frag = new AcFragment();
                break;
        }
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(0x7fff0001, frag);
        ft.commit();
    }

    private void setTabStyle(TextView tv, boolean active) {
        if (active) {
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setBackgroundColor(Color.parseColor("#0f3460"));
            activeTab = tv;
        } else {
            tv.setTextColor(Color.LTGRAY);
            tv.setTypeface(Typeface.DEFAULT);
            tv.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
