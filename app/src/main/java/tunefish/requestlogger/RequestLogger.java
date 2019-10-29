package tunefish.requestlogger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


public class RequestLogger implements IXposedHookLoadPackage {
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        Class realCall = XposedHelpers.findClassIfExists("okhttp3.RealCall", lpparam.classLoader);
        if (realCall != null) {
            // Reuse hook because we're hooking multiple methods of the same class
            XC_MethodHook hook = new RealCallHook();

            // Call.execute() is used to issue a synchronous request
            XposedHelpers.findAndHookMethod(realCall,"execute", hook);

            // Call.enqueue(Callback c) is used to issue an asynchronous request
            XposedHelpers.findAndHookMethod(realCall, "enqueue","okhttp3.Callback", hook);

            XposedBridge.log("Hooked app: " + lpparam.packageName + " with okhttp3");
        }
    }
}
