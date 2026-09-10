package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    protected XC_MethodHook() {}

    protected XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public MethodHookParam() {}

        public Object[] args;

        public Member method;

        public Object getResult() {
            return null;
        }

        public void setResult(Object result) {}

        public Throwable getThrowable() {
            return null;
        }

        public void setThrowable(Throwable throwable) {}

        public Object getThisObject() {
            return null;
        }
    }

    public static class Unhook {
        public Member getHookedMethod() {
            return null;
        }

        public XC_MethodHook getCallback() {
            return null;
        }

        public void unhook() {}
    }
}
