package com.zai.yfac;

import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * YfVehicle — reflection wrapper around the YF Tech vehicle SDK.
 *
 * The classes live in the device's shared libraries (com.yftech.vehicle.sdk,
 * com.yftech.vehicle.internal.adapter). They are not packaged inside this app;
 * they are loaded at runtime when the app declares <uses-library> in its
 * manifest. We access them via reflection so the app has no compile-time
 * dependency on the SDK (no SDK jar on the build classpath).
 *
 * All exceptions are caught and logged to the status TextView so the user sees
 * SDK load failures inline in the UI instead of crashing the activity.
 */
public final class YfVehicle {
    private static final String TAG = "YfVehicle";

    /** Tag displayed in the status bar at the bottom of MainActivity. */
    public static TextView statusView = null;

    // ---- Known SDK class names (from smali analysis) ----
    private static final String C_AIR_CONDITION_MGR  = "com.yftech.vehicle.sdk.AirConditionManager";
    private static final String C_CAR_PARKING_MGR    = "com.yftech.vehicle.sdk.CarParkingManager";
    private static final String C_CAR_PARTS_MGR      = "com.yftech.settings.manager.CarPartsManager";
    private static final String C_BASE_API            = "com.yftech.vehicle.internal.adapter.IBaseApi";
    private static final String C_AIR_CONDITION_IF   = "com.yftech.vehicle.internal.adapter.IAirCondition";
    private static final String C_CAR_PARKING_IF     = "com.yftech.vehicle.internal.adapter.ICarParking"; // may or may not exist

    /** Cache of resolved classes (FQN -> Class). */
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();

    private YfVehicle() {}

    public static void setStatus(String msg) {
        if (statusView != null) {
            statusView.setText(msg);
        }
        Log.i(TAG, "status: " + msg);
    }

    public static void setError(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        if (statusView != null) {
            statusView.setText("ERR: " + msg + " — " + (t == null ? "" : t.getMessage()));
        }
    }

    /** Try to load a class by name; returns null if the class isn't on the classpath. */
    public static Class<?> cls(String name) {
        if (CLASS_CACHE.containsKey(name)) {
            return CLASS_CACHE.get(name);
        }
        Class<?> c = null;
        try {
            c = Class.forName(name, false, YfVehicle.class.getClassLoader());
        } catch (Throwable t) {
            Log.w(TAG, "class not found: " + name + " — " + t.getMessage());
        }
        CLASS_CACHE.put(name, c);
        return c;
    }

    /** Invoke a static method (e.g. AirConditionManager.get()) via reflection. */
    public static Object invokeStatic(String className, String method, Class<?>[] argTypes, Object[] args) {
        try {
            Class<?> c = cls(className);
            if (c == null) {
                setStatus("SDK class missing: " + className);
                return null;
            }
            Method m = c.getMethod(method, argTypes);
            return m.invoke(null, args);
        } catch (Throwable t) {
            setError("invokeStatic " + className + "." + method + " failed", t);
            return null;
        }
    }

    /** Invoke an instance method on the given object. */
    public static Object invoke(Object obj, String method, Class<?>[] argTypes, Object[] args) {
        if (obj == null) {
            setStatus("invoke on null: " + method);
            return null;
        }
        try {
            Method m = obj.getClass().getMethod(method, argTypes);
            return m.invoke(obj, args);
        } catch (Throwable t) {
            setError("invoke " + obj.getClass().getName() + "." + method + " failed", t);
            return null;
        }
    }

    /** Lookup a singleton manager instance (e.g. AirConditionManager.get()). */
    public static Object getManager(String className) {
        return invokeStatic(className, "get", new Class[0], new Object[0]);
    }

    /** Lookup a SettingsManager-style singleton (e.g. CarPartsManager.getInstance()). */
    public static Object getManagerInstance(String className) {
        return invokeStatic(className, "getInstance", new Class[0], new Object[0]);
    }

    /** Best-effort lookup of an enum constant by FQN enum name + value name. */
    public static Object enumValue(String enumFqn, String valueName) {
        try {
            Class<?> c = cls(enumFqn);
            if (c == null) {
                setStatus("enum class missing: " + enumFqn);
                return null;
            }
            Object[] constants = c.getEnumConstants();
            if (constants == null) return null;
            for (Object o : constants) {
                if (o.toString().equals(valueName)) return o;
            }
            // case-insensitive fallback
            for (Object o : constants) {
                if (o.toString().equalsIgnoreCase(valueName)) return o;
            }
            setStatus("enum value not found: " + enumFqn + "." + valueName);
            return null;
        } catch (Throwable t) {
            setError("enum lookup failed: " + enumFqn + "." + valueName, t);
            return null;
        }
    }

    /** True if all the requested SDK manager classes are present (used to gate UI). */
    public static boolean isSdkAvailable() {
        return cls(C_AIR_CONDITION_MGR) != null
            || cls(C_CAR_PARKING_MGR) != null
            || cls(C_CAR_PARTS_MGR) != null;
    }

    // ---- Convenience accessors ----

    public static Object airConditionManager() {
        return getManager(C_AIR_CONDITION_MGR);
    }

    public static Object carParkingManager() {
        return getManager(C_CAR_PARKING_MGR);
    }

    public static Object carPartsManager() {
        return getManagerInstance(C_CAR_PARTS_MGR);
    }

    // EnableState enum lives in IBaseApi.EnableState — values ENABLED, DISABLED, DEFAULT
    public static Object enableState(boolean enabled) {
        Object state = enumValue(C_BASE_API + "$EnableState", enabled ? "ENABLED" : "DISABLED");
        if (state != null) return state;
        // Fallback — some platforms might use a different enum shape. Just return Boolean.
        return Boolean.valueOf(enabled);
    }

    // FanSpeed enum lives in IAirCondition.FanSpeed — values LOW, NORMAL, HIGH
    public static Object fanSpeed(String name) {
        return enumValue(C_AIR_CONDITION_IF + "$FanSpeed", name);
    }

    // ComfortCurveMode enum lives in IAirCondition.ComfortCurveMode — values SOFT, NORMAL, FAST
    public static Object comfortCurveMode(String name) {
        return enumValue(C_AIR_CONDITION_IF + "$ComfortCurveMode", name);
    }

    // AirCirculationMode enum lives in IAirCondition.AirCirculationMode — values INTERNAL, EXTERNAL, etc.
    public static Object airCirculationMode(String name) {
        return enumValue(C_AIR_CONDITION_IF + "$AirCirculationMode", name);
    }
}
