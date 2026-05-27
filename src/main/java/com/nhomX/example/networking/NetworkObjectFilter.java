package com.nhomX.example.networking;

import java.io.ObjectInputFilter;
import java.util.Map;
import java.util.Set;

/**
 * Defensive allow-list for objects received from clients over ObjectInputStream.
 */
final class NetworkObjectFilter {
    private static final long MAX_DEPTH = 40;
    private static final long MAX_REFERENCES = 50_000;
    private static final long MAX_STREAM_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_ARRAY_LENGTH = 50L * 1024L * 1024L;

    private static final Set<Class<?>> ALLOWED_EXACT_CLASSES = Set.of(
            Object.class,
            String.class,
            Boolean.class,
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Character.class
    );

    private NetworkObjectFilter() {}

    static ObjectInputFilter create() {
        return NetworkObjectFilter::checkInput;
    }

    private static ObjectInputFilter.Status checkInput(ObjectInputFilter.FilterInfo info) {
        if (isOverLimit(info)) {
            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> serialClass = info.serialClass();
        if (serialClass == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }

        return isAllowed(serialClass)
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
    }

    private static boolean isOverLimit(ObjectInputFilter.FilterInfo info) {
        return info.depth() > MAX_DEPTH
                || info.references() > MAX_REFERENCES
                || info.streamBytes() > MAX_STREAM_BYTES
                || info.arrayLength() > MAX_ARRAY_LENGTH;
    }

    private static boolean isAllowed(Class<?> serialClass) {
        if (serialClass.isArray()) {
            return isAllowedArray(serialClass);
        }
        if (serialClass.isPrimitive()
                || serialClass.isEnum()
                || Enum.class.isAssignableFrom(serialClass)) {
            return true;
        }
        if (ALLOWED_EXACT_CLASSES.contains(serialClass)) {
            return true;
        }

        String className = serialClass.getName();
        if (className.equals(Message.class.getName())) {
            return true;
        }
        if (className.startsWith("com.nhomX.example.model.")) {
            return true;
        }
        if (className.startsWith("java.time.")) {
            return true;
        }
        if (className.startsWith("java.util.Collections$")
                || className.startsWith("java.util.Arrays$")
                || className.startsWith("java.util.ImmutableCollections$")
                || className.startsWith("java.util.HashMap$")
                || className.startsWith("java.util.LinkedHashMap$")) {
            return true;
        }
        return isAllowedCollectionOrMap(serialClass);
    }

    private static boolean isAllowedArray(Class<?> serialClass) {
        Class<?> componentType = serialClass.getComponentType();
        while (componentType.isArray()) {
            componentType = componentType.getComponentType();
        }
        return componentType.isPrimitive()
                || componentType.getName().startsWith("java.util.")
                || isAllowed(componentType);
    }

    private static boolean isAllowedCollectionOrMap(Class<?> serialClass) {
        return java.util.List.class.isAssignableFrom(serialClass)
                || Set.class.isAssignableFrom(serialClass)
                || Map.class.isAssignableFrom(serialClass);
    }
}
