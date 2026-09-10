package app.xagefixer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class XAgeRestrictionFixerHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.twitter.android";
    private static final String WRAPPER_TYPE = "TweetWithVisibilityResults";
    private static final Set<ClassLoader> INSTALLED_CLASSLOADERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final byte[] WRAPPER_MARKER_BYTES =
            WRAPPER_TYPE.getBytes(StandardCharsets.US_ASCII);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)
                || !TARGET_PACKAGE.equals(lpparam.processName)
                || lpparam.classLoader == null) {
            return;
        }

        synchronized (INSTALLED_CLASSLOADERS) {
            if (!INSTALLED_CLASSLOADERS.add(lpparam.classLoader)) {
                return;
            }
        }

        hookResponseBodies(lpparam.classLoader);
        hookJsonInputConstructors(lpparam.classLoader);
        hookStringParsers(lpparam.classLoader);
    }

    private static void hookResponseBodies(ClassLoader classLoader) {
        String[] classNames = {
                "okhttp3.ResponseBody",
                "com.android.okhttp.ResponseBody"
        };

        for (String className : classNames) {
            Class<?> responseBody = loadClass(className, classLoader);
            if (responseBody == null) {
                continue;
            }

            for (Method method : responseBody.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0
                        || (method.getReturnType() != byte[].class
                        && method.getReturnType() != String.class)
                        || (!"bytes".equals(method.getName())
                        && !"string".equals(method.getName()))) {
                    continue;
                }
                hook(method, new ResponseBodyHook());
            }
        }
    }

    private static void hookJsonInputConstructors(ClassLoader classLoader) {
        String[] classNames = {
                "org.json.JSONTokener",
                "org.json.JSONObject",
                "org.json.JSONArray"
        };

        for (String className : classNames) {
            Class<?> jsonClass = loadClass(className, classLoader);
            if (jsonClass == null) {
                continue;
            }

            for (Constructor<?> constructor : jsonClass.getDeclaredConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length > 0 && parameterTypes[0] == String.class) {
                    hook(constructor, new JsonInputHook());
                }
            }
        }
    }

    private static void hookStringParsers(ClassLoader classLoader) {
        String[][] parserMethods = {
                {"com.google.gson.Gson", "fromJson"},
                {"com.google.gson.JsonParser", "parse", "parseString"},
                {"com.squareup.moshi.JsonAdapter", "fromJson"}
        };

        for (String[] parser : parserMethods) {
            Class<?> parserClass = loadClass(parser[0], classLoader);
            if (parserClass == null) {
                continue;
            }

            for (Method method : parserClass.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0
                        || method.getParameterTypes()[0] != String.class
                        || !hasName(parser, method.getName())) {
                    continue;
                }
                hook(method, new JsonInputHook());
            }
        }
    }

    private static boolean hasName(String[] names, String candidate) {
        for (int index = 1; index < names.length; index += 1) {
            if (names[index].equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> loadClass(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void hook(java.lang.reflect.Member member, XC_MethodHook callback) {
        try {
            XposedBridge.hookMethod(member, callback);
        } catch (Throwable error) {
            XposedBridge.log("X Age Restriction Fixer could not hook " + member.getName());
        }
    }

    private static boolean containsMarker(byte[] body) {
        if (body.length < WRAPPER_MARKER_BYTES.length) {
            return false;
        }

        for (int index = 0; index <= body.length - WRAPPER_MARKER_BYTES.length; index += 1) {
            boolean matches = true;
            for (int offset = 0; offset < WRAPPER_MARKER_BYTES.length; offset += 1) {
                if (body[index + offset] != WRAPPER_MARKER_BYTES[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static final class ResponseBodyHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object result = param.getResult();
            if (result instanceof String) {
                String body = (String) result;
                if (body.indexOf(WRAPPER_TYPE) < 0) {
                    return;
                }
                String normalized = JsonNormalizer.normalize(body);
                if (!normalized.equals(body)) {
                    param.setResult(normalized);
                }
            } else if (result instanceof byte[]) {
                byte[] body = (byte[]) result;
                if (!containsMarker(body)) {
                    return;
                }
                String decoded = new String(body, StandardCharsets.UTF_8);
                String normalized = JsonNormalizer.normalize(decoded);
                if (!normalized.equals(decoded)) {
                    param.setResult(normalized.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static final class JsonInputHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof String)) {
                return;
            }

            String body = (String) param.args[0];
            if (body.indexOf(WRAPPER_TYPE) < 0) {
                return;
            }

            param.args[0] = JsonNormalizer.normalize(body);
        }
    }
}
