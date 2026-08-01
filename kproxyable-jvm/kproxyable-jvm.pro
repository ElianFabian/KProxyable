# 1. Keep the Master Registry (KProxyJvmImpl)
-keep class com.elianfabian.kproxyable.generated.KProxyJvmImpl {
    public static final com.elianfabian.kproxyable.generated.KProxyJvmImpl INSTANCE;
    public <T> T createProxy(com.elianfabian.kproxyable.ProxyHandler, kotlin.reflect.KClass);
}

# 2. Keep all generated module registry fragments
-keep class com.elianfabian.kproxyable.generated.KProxyRegistry_* {
    public static final com.elianfabian.kproxyable.generated.KProxyRegistry_* INSTANCE;
    public <T> T createProxy(com.elianfabian.kproxyable.ProxyHandler, kotlin.reflect.KClass);
}

# 3. Keep all generated Proxy classes (Convention: _*Proxy)
-keep class **._*Proxy {
    <init>(com.elianfabian.kproxyable.ProxyHandler);
}

# 4. Preserve the ServiceLoader breadcrumbs (used by KSP during build)
-keepresources META-INF/services/com.elianfabian.kproxyable.KProxyFactory
