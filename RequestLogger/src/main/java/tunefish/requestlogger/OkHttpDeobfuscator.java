package tunefish.requestlogger;

import android.annotation.SuppressLint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import de.robv.android.xposed.XposedHelpers;
import io.herrmann.generator.Generator;


class OkHttpDeobfuscator {
    private static class Step2Container {
        Class<?> callInterface;
        Class<?> requestClass;
        Method requestGetter;

        Step2Container(Class<?> callInterface, Class<?> requestClass, Method requestGetter) {
            this.callInterface = callInterface;
            this.requestClass = requestClass;
            this.requestGetter = requestGetter;
        }
    }

    private static class DeobfExeption extends Exception {
        DeobfExeption(String message) {
            super(message);
        }
    }

    static class DeobfuscationResult {

        Method execute;
        Method enqueue;
        String urlField;
        String requestGetter;

        DeobfuscationResult(Method execute, Method enqueue, String urlField, String requestGetter) {
            this.execute = execute;
            this.enqueue = enqueue;
            this.urlField = urlField;
            this.requestGetter = requestGetter;
        }
    }

    // =============================================================================================
    // Main routine
    // =============================================================================================

    static boolean hasObfuscatedOkHttp(ClassLoader cl) {
        return XposedHelpers.findClassIfExists("okhttp3.a", cl) != null;
    }

    static DeobfuscationResult deobfuscate(ClassLoader cl) {
        // Step one: find all classes in main okhttp3 package
        List<Class<?>> classes = discoverClasses("okhttp3", cl);
        List<Class<?>> interfaces = discoverInterfaces("okhttp3", cl);

        if (classes.size() == 0 || interfaces.size() == 0) {
            Utils.log("Only found %d classes and %d interfaces", classes.size(), interfaces.size());
            return null;
        }

        // Step two: find the okhttp3.Call interface, okhttp3.Request class
        // and okhttp3.Call.request() method
        Class<?> callInterface, requestClass;
        Method requestGetter;
        try {
            Step2Container container = findCallInterface(interfaces);

            if (container == null) {
                throw new DeobfExeption("Found no matching Call interface");
            }
            callInterface = container.callInterface;
            requestClass = container.requestClass;
            requestGetter = container.requestGetter;
        } catch (DeobfExeption e) {
            Utils.log("Failed to find Call interface:", e);
            Utils.log(Utils.dumpClasses(interfaces));
            return null;
        }

        // Step three: find the okhttp3.Call.execute() and okhttp3.Call.enqueue(Callback c) methods
        Method executeMethod, enqueueMethod;
        try {
            executeMethod = findExecute(callInterface, classes);
            enqueueMethod = findEnqueue(callInterface, interfaces);

            if (executeMethod == null || enqueueMethod == null) {
                throw new DeobfExeption(
                        "Found no matching execute()/enqueue() methods of Call interface");
            }
        } catch (DeobfExeption e) {
            Utils.log("Failed to find execute()/enqueue() methods of Call interface:", e);
            Utils.log(Utils.dumpClass(callInterface));
            return null;
        }

        // Step four: find the okhttp3.RealCall class
        Class<?> realCallClass;
        try {
            realCallClass = findRealClass(callInterface, classes);

            if (realCallClass == null) {
                throw new DeobfExeption(
                        "Found no matching RealCall class implementing Call interface");
            }
        } catch (DeobfExeption e) {
            Utils.log("Failed to find RealCall class:", e);
            Utils.log("Interface:\n%s\nClasses:\n%s",
                      Utils.dumpClass(callInterface),
                      Utils.dumpClasses(classes));
            return null;
        }

        // Step five: get the okhttp3.RealCall.execute()
        // and okhttp3.RealCall.enqueue() methods
        try {
            executeMethod = getImplementationIn(realCallClass, executeMethod);
            enqueueMethod = getImplementationIn(realCallClass, enqueueMethod);
        } catch (NoSuchMethodException e) {
            Utils.log("Failed to find execute()/enqueue() implementations of RealCall class:", e);
            Utils.log("Guess for: execute(): %s, enqueue(): %s", executeMethod, enqueueMethod);
            Utils.log("Call interface:\n%s\nRealClass class:\n%s",
                      Utils.dumpClass(callInterface),
                      Utils.dumpClass(realCallClass));
            return null;
        }

        // Step six: find the okhttp3.HttpUrl class
        Field urlField;
        try {
            urlField = findRequestUrlField(requestClass, classes);

            if (urlField == null) {
                throw new DeobfExeption("Failed to find url field of Request class");
            }
        } catch (DeobfExeption e) {
            Utils.log("Failed to find the url field of Request class:", e);
            Utils.log("Request class:\n%s\nClasses:\n%s",
                      Utils.dumpClass(requestClass),
                      Utils.dumpClasses(classes));
            return null;
        }

        return new DeobfuscationResult(
                executeMethod,
                enqueueMethod,
                urlField.getName(),
                requestGetter.getName());
    }

    // =============================================================================================
    // Step 1 utils: discovering obfuscated classes and interfaces
    // =============================================================================================

    private static List<Class<?>> discoverClasses(String pkg, ClassLoader cl) {
        return discoverAllClasses(pkg, cl, (Class c) -> !c.isInterface());
    }

    private static List<Class<?>> discoverInterfaces(String pkg, ClassLoader cl) {
        return discoverAllClasses(pkg, cl, Class::isInterface);
    }

    @SuppressLint("NewApi")
    private static List<Class<?>> discoverAllClasses(String pkg,
                                                     ClassLoader cl,
                                                     Predicate<Class> tester) {
        List<Class<?>> classes = new ArrayList<>();
        for (String seq : alphaSequenceGenerator()) {
            Class c = XposedHelpers.findClassIfExists(pkg + "." + seq, cl);
            if (c == null) {
                // we have enumerated all classes
                break;
            }

            if (tester.test(c)) {
                classes.add(c);
            }
        }
        return classes;
    }

    private static Generator<String> alphaSequenceGenerator() {
        return new Generator<String>() {
            @Override
            protected void run() throws InterruptedException {
                int length = 1;
                while (length > 0) {
                    for (String x : genStringWithLength("", length++)) {
                        yield(x);
                    }
                }
            }
        };
    }

    private static Generator<String> genStringWithLength(final String prefix, final int len) {
        return new Generator<String>() {
            @Override
            protected void run() throws InterruptedException {
                if (len <= 0) {
                    yield(prefix);
                } else {
                    for (int i = 0; i < 26; ++i) {
                        for (String x : genStringWithLength(prefix + ((char) (97 + i)), len-1)) {
                            yield(x);
                        }
                    }
                }
            }
        };
    }

    // =============================================================================================
    // Step 2 utils: identifying the okhttp3.Call interface, okhttp3.Request class
    // and request getter okhttp3.Call.request()
    // =============================================================================================

    private static Step2Container findCallInterface(List<Class<?>> interfaces) throws DeobfExeption {
        Step2Container container = null;
        for (Class nterface : interfaces) {
            Class[] subclasses = nterface.getDeclaredClasses();
            if (subclasses.length != 1 || !subclasses[0].isInterface()) {
                continue;
            }

            Method[] subclassMethods = subclasses[0].getDeclaredMethods();
            if (subclassMethods.length != 1
                    || subclassMethods[0].getReturnType() != nterface) {
                continue;
            }

            Class<?>[] paramTypes = subclassMethods[0].getParameterTypes();
            if (paramTypes.length != 1) {
                continue;
            }

            // Guess that the first parameter type of the only method
            // in the sub-interface is the request class
            Class<?> potentialReqClass = paramTypes[0];
            Method reqGetter = findRequestGetter(nterface, potentialReqClass);

            if (reqGetter != null) {
                if (container == null) {
                    container = new Step2Container(nterface,
                                                   potentialReqClass,
                                                   reqGetter);
                } else {
                    throw new DeobfExeption(String.format(
                            "Multiple potential matches for Call interface: %s and %s",
                            container.callInterface,
                            nterface));
                }
            }
        }

        return container;
    }

    private static Method findRequestGetter(Class<?> nterface, Class<?> requestGuess)
            throws DeobfExeption {
        Method requestGetter = null;
        for (Method method : nterface.getDeclaredMethods()) {
            // The interface method must return an instance of
            // what we guess is the Request class and accept no parameters
            if (method.getReturnType() != requestGuess
                    || method.getParameterTypes().length != 0) {
                continue;
            }

            if (requestGetter == null) {
                requestGetter = method;
            } else {
                throw new DeobfExeption(String.format(
                        "Multiple potential matches for okhttp3.Call.request(): %s and %s",
                        requestGetter,
                        method));
            }
        }
        return requestGetter;
    }

    // =============================================================================================
    // Step 3 utils: finding okhttp3.Call.execute() and okhttp3.Call.enqueue(Callback c) methods
    // =============================================================================================

    private static Method findExecute(Class<?> callInterface, List<Class<?>> classes)
            throws DeobfExeption {
        Method executeMethod = null;
        for (Method method : callInterface.getDeclaredMethods()) {
            // execute() method can throw an exception
            // and must accept no parameters
            // and return an instance of some class in the okhttp3 package
            if (method.getExceptionTypes().length == 0
                    || !classes.contains(method.getReturnType())
                    || method.getParameterTypes().length != 0) {
                continue;
            }

            if (executeMethod == null) {
                executeMethod = method;
            } else {
                throw new DeobfExeption(String.format(
                        "Multiple potential matches for okhttp3.Call.execute(): %s and %s",
                        executeMethod,
                        method));
            }
        }
        return executeMethod;
    }

    private static Method findEnqueue(Class<?> callInterface, List<Class<?>> interfaces)
            throws DeobfExeption {
        Method enqueueMethod = null;
        for (Method method : callInterface.getDeclaredMethods()) {
            // enqueue() method cannot throw an exception
            // and must be a method taking one parameter,
            // an instance of an interface in the okhttp3 package,
            // and return void
            if (method.getExceptionTypes().length > 0
                    || method.getParameterTypes().length != 1
                    || !interfaces.contains(method.getParameterTypes()[0])
                    || method.getReturnType() != Void.TYPE) {
                continue;
            }

            if (enqueueMethod == null) {
                enqueueMethod = method;
            } else {
                throw new DeobfExeption(String.format(
                        "Multiple potential matches for okhttp3.Call.enqueue(Callback c): %s and %s",
                        enqueueMethod,
                        method));
            }
        }
        return enqueueMethod;
    }

    // =============================================================================================
    // Step 4 utils: finding the okhttp3.RealClass
    // =============================================================================================

    private static Class<?> findRealClass(Class<?> callInterface, List<Class<?>> classes)
            throws DeobfExeption {
        Class<?> realCallClass = null;
        for (Class clazz : classes) {
            if (callInterface.isAssignableFrom(clazz)) {
                if (realCallClass == null) {
                    realCallClass = clazz;
                } else {
                    throw new DeobfExeption(String.format(
                            "Multiple potential matches for okhttp3.RealCall: %s and %s",
                            realCallClass,
                            clazz));
                }
            }
        }
        return realCallClass;
    }

    // =============================================================================================
    // Step 5 utils: getting okhttp3.RealCall.execute() and okhttp3.RealCall.enqueue(Callback c)
    // =============================================================================================

    private static Method getImplementationIn(Class<?> realCallClass, Method interfaceEnqueue)
            throws NoSuchMethodException {
        return realCallClass.getMethod(interfaceEnqueue.getName(),
                                       interfaceEnqueue.getParameterTypes());
    }

    // =============================================================================================
    // Step 6 utils: finding the url field of okhttp3.Request and the okhttp3.HttpUrl class
    // =============================================================================================

    private static Field findRequestUrlField(Class<?> requestClass, List<Class<?>> classes)
            throws DeobfExeption {
        Field urlField = null;

        for (Field field : requestClass.getDeclaredFields()) {
            // Field must contain an instance of a class of the okhttp3 package
            if (!classes.contains(field.getType())) {
                continue;
            }

            // HttpUrl class must have a method with return type java.net.URI
            for (Method m : field.getType().getDeclaredMethods()) {
                if (m.getReturnType() != java.net.URI.class) {
                    continue;
                }

                if (urlField == null) {
                    urlField = field;
                } else {
                    throw new DeobfExeption(String.format(
                            "Multiple potential matches for okhttp3.HttpUrl: %s and %s",
                            urlField.getType(),
                            field.getType()));
                }
            }
        }

        return urlField;
    }
}
