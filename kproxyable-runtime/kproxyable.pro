# 1. Keep the Master Registry (KProxyJvmImpl)
-keep class com.elianfabian.kproxyable.generated.KProxyJvmImpl {
    public static final com.elianfabian.kproxyable.generated.KProxyJvmImpl INSTANCE;
    *;
}

# 2. Keep the Entry Point (KProxyJvm)
-keep class com.elianfabian.kproxyable.KProxyJvm {
    public static final com.elianfabian.kproxyable.KProxyJvm INSTANCE;
    *;
}

# 3. Keep all Module Registries (they implement KProxyFactory)
-keep class * implements com.elianfabian.kproxyable.KProxyFactory { *; }

# 4. Keep generated Proxy classes
# They follow the pattern _ClassNameProxy
-keep class **._*Proxy { *; }
