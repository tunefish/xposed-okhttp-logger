package tunefish.requestlogger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


public class RequestLogger implements IXposedHookLoadPackage {
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        // Only hook applications using OkHttp
        Class realCall = XposedHelpers.findClassIfExists("okhttp3.RealCall",
                                                         lpparam.classLoader);
        if (realCall != null) {
            // RealCall.execute() and RealCall.enqueue(Callback c) are used to
            // issue synchronous requests (and asynchronous, respectively).
            XposedHelpers.findAndHookMethod(realCall,
                                            "execute",
                                            new RealCallHook());
            XposedHelpers.findAndHookMethod(realCall,
                                            "enqueue",
                                            "okhttp3.Callback",
                                            new RealCallHook());

            XposedBridge.log("REQUESTLOGGER: Hooked " + lpparam.packageName);
        }
    }
}
