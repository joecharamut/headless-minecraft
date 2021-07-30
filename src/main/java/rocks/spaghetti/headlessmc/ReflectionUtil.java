package rocks.spaghetti.headlessmc;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;
import org.apache.commons.collections4.MapUtils;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ReflectionUtil {
    private ReflectionUtil() {
        throw new IllegalStateException("Utility Class");
    }

    private static final Map<Class<?>, String> primitiveTypes = MapUtils.putAll(new HashMap<>(), new Object[] {
            byte.class, "B",
            char.class, "C",
            double.class, "D",
            float.class, "F",
            int.class, "I",
            long.class, "J",
            short.class, "S",
            void.class, "V",
            boolean.class, "Z"
    });
    public static String toClassDescriptor(Class<?> type) {
        if (type.isArray()) {
            return "[" + toClassDescriptor(type.componentType());
        } else if (type.isPrimitive()) {
            return primitiveTypes.get(type);
        } else {
            return "L" + type.getName().replace(".", "/") + ";";
        }
    }

    public static String toMethodDescriptor(Method m) {
        StringBuilder builder = new StringBuilder();
        builder.append(m.getName());
        builder.append('(');

        for (Class<?> param : m.getParameterTypes()) {
            builder.append(toClassDescriptor(param));
        }

        builder.append(')');
        builder.append(toClassDescriptor(m.getReturnType()));

        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> proxyClass, Object target) {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(proxyClass);
        factory.setFilter(m -> true);
        Class<?> clazz = factory.createClass();

        Map<String, Method> proxyMethods = new HashMap<>();
        Class<?> targetClass = target.getClass();
        for (Method m : targetClass.getMethods()) {
            proxyMethods.put(toMethodDescriptor(m), m);
        }
        for (Method m : targetClass.getDeclaredMethods()) {
            proxyMethods.put(toMethodDescriptor(m), m);
        }

        MethodHandler handler = (self, thisMethod, proceed, args) -> {
            Method m = proxyMethods.get(toMethodDescriptor(thisMethod));
            if (m == null) throw new NoSuchMethodException("Method Not Proxied: " + thisMethod);
            return m.invoke(target, args);
        };

        Object obj = new ObjenesisStd().getInstantiatorOf(clazz).newInstance();
        ((Proxy) obj).setHandler(handler);
        return (T) obj;
    }

    public static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getField(name);
        } catch (NoSuchFieldException e) {
            return clazz.getDeclaredField(name);
        }
    }

    public static void setStaticField(Class<?> clazz, String fieldName, Object newValue) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, newValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setField(Object instance, String fieldName, Object newValue) {
        try {
            Field field = getField(instance.getClass(), fieldName);
            field.setAccessible(true);
            field.set(instance, newValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
