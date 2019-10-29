package tunefish.requestlogger;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;

import java.io.FileOutputStream;
import java.sql.Timestamp;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;


public class RealCallHook extends XC_MethodHook {
    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        // Called whenever a okhttp3.Request object has been built and was
        // scheduled to be executed (but has not been issued yet)
        try {
            // Get the okhttp3.Request object form the RealCall object
            Object request = XposedHelpers.callMethod(param.thisObject, "request");
            if (request == null) {
                XposedBridge.log("REQUESTLOGGER: Got call but REQUEST is null");
                return;
            }

            // Get the okhttp3.HttpUrl object from the Request
            Object url = XposedHelpers.getObjectField(request, "url");

            if (url == null) {
                XposedBridge.log("REQUESTLOGGER: Got call but URL is null");
                return;
            }

            Application app = AndroidAppHelper.currentApplication();
            if (app == null) {
                XposedBridge.log("REQUESTLOGGER: Cannot get application context!");
                return;
            }

            // Append to "_requests.log" in internal storage of the hooked app
            FileOutputStream fos = app.openFileOutput("_requests.log", Context.MODE_APPEND);
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            fos.write(String.format("%-31s %s\n", timestamp, url).getBytes());
            fos.close();
        } catch (Exception e) {
            // Log error and don't crash
            XposedBridge.log("REQUESTLOGGER: okhttp3 request could not be identified:");
            XposedBridge.log(e);
        }
    }
}
