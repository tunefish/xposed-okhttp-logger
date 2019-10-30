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
    // The name of the getter function in okhttp3.Call which returns
    // the associated Request object
    private String reqGetter;

    // The name of the URL field in okhttp3.Request
    private String url;

    RealCallHook() {
        // default values in unobfuscated OkHttp
        this("request", "url");
    }

    RealCallHook(String reqGetter, String url) {
        this.reqGetter = reqGetter;
        this.url = url;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        // Called whenever a okhttp3.RealCall object is executed or scheduled
        try {
            // Get the okhttp3.Request object form the RealCall object
            Object req = XposedHelpers.callMethod(param.thisObject, reqGetter);
            if (req == null) {
                XposedBridge.log("REQUESTLOGGER: Request is null");
                return;
            }

            // Get the okhttp3.HttpUrl object from the Request
            Object url = XposedHelpers.getObjectField(req, this.url);

            if (url == null) {
                XposedBridge.log("REQUESTLOGGER: url is null");
                return;
            }

            Application app = AndroidAppHelper.currentApplication();
            if (app == null) {
                XposedBridge.log("REQUESTLOGGER: Cannot get app context!");
                return;
            }

            // Append to "_requests.log" in internal storage of the hooked app
            FileOutputStream fos = app.openFileOutput("_requests.log",
                                                      Context.MODE_APPEND);
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            fos.write(String.format("%-31s %s\n", timestamp, url).getBytes());

            // there's no way to clean up when app is closed/phone is turned off
            //  -> need to reopen + close for every logged URL
            fos.close();
        } catch (Exception e) {
            // Log error and don't crash
            XposedBridge.log("REQUESTLOGGER: failed to get request URLg:");
            XposedBridge.log(e);
        }
    }
}
