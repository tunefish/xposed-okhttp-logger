package tunefish.requestlogger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import tunefish.requestlogger.OkHttpDeobfuscator.DeobfuscationResult;


public class LoggerHookInstaller implements IXposedHookLoadPackage {
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        Class realCall = XposedHelpers.findClassIfExists("okhttp3.RealCall",
                                                         lpparam.classLoader);
        if (realCall != null) {
            // This app uses OkHttp and makes no use of code obfuscation
            // (at least not for libraries) -> we can hook directly
            XposedHelpers.findAndHookMethod(
                    realCall, "execute", new RealCallHook());
            XposedHelpers.findAndHookMethod(
                    realCall, "enqueue", "okhttp3.Callback", new RealCallHook());

            Utils.log("REQUESTLOGGER: Hooked %s", lpparam.packageName);
            return;
        }

        if (OkHttpDeobfuscator.hasObfuscatedOkHttp(lpparam.classLoader)) {
            // We're dealing with an obfuscated version of OkHttp3..
            Utils.log("REQUESTLOGGER: Attempting to hook obfuscated %s", lpparam.packageName);

            DeobfuscationResult result = OkHttpDeobfuscator.deobfuscate(lpparam.classLoader);
            if (result == null) {
                return;
            }

            XposedBridge.hookMethod(
                    result.execute, new RealCallHook(result.requestGetter, result.urlField));
            XposedBridge.hookMethod(
                    result.enqueue, new RealCallHook(result.requestGetter, result.urlField));
            Utils.log("REQUESTLOGGER: Hooked %s, %s", result.execute, result.enqueue);
        }
    }
}
