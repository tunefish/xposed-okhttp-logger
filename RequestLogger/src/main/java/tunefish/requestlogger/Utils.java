package tunefish.requestlogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

class Utils {
    // =============================================================================================
    // General utils
    // =============================================================================================

    static void log(String message, Throwable t) {
        XposedBridge.log(message);
        XposedBridge.log(t);
    }

    static void log(String format, Object ...args) {
        XposedBridge.log(String.format(format, args));
    }

    static String dumpClass(Class<?> clazz) {
        return dumpClasses(Collections.singletonList(clazz));
    }

    static String dumpClasses(List<Class<?>> classes) {
        return dumpClasses(classes, 0);
    }

    static String dumpClasses(List<Class<?>> classes, int depth) {
        StringBuilder sb = new StringBuilder();
        for (Class clazz : classes) {
            sb.append(repeatString("  ", depth));
            sb.append(clazz);
            sb.append(" {\n");

            for (Class<?> subclass : clazz.getDeclaredClasses()) {
                sb.append(dumpClasses(Collections.singletonList(subclass), depth+1));
            }

            for (Field field : clazz.getDeclaredFields()) {
                sb.append(repeatString("  ", depth + 1));
                sb.append(field);
                sb.append('\n');
            }

            for (Method method : clazz.getMethods()) {
                sb.append(repeatString("  ", depth + 1));
                sb.append(method);
                sb.append('\n');
            }

            sb.append(repeatString("  ", depth));
            sb.append("}\n");
        }
        return sb.toString();
    }

    static String repeatString(String s, int count) {
        if (count == 0) {
            return "";
        } else {
            return new String(new char[count]).replace("\0", s);
        }
    }
}
