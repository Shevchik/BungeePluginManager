package bungeepluginmanager;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {

	public static final <T extends AccessibleObject> T setAccessible(T t) {
		t.setAccessible(true);
		return t;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFieldValue(Object obj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
		Class<?> clazz = obj.getClass();
		do {
			for (Field field : clazz.getDeclaredFields()) {
				if (field.getName().equals(fieldName)) {
					return (T) setAccessible(clazz.getDeclaredField(fieldName)).get(obj);
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);
		throw new NoSuchFieldException("Can't find field " + fieldName);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getStaticFieldValue(Class<?> clazz, String fieldName) throws IllegalAccessException, NoSuchFieldException {
		do {
			for (Field field : clazz.getDeclaredFields()) {
				if (field.getName().equals(fieldName)) {
					return (T) setAccessible(clazz.getDeclaredField(fieldName)).get(null);
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);
		throw new NoSuchFieldException("Can't find field " + fieldName);
	}

	public static void setFieldValue(Object obj, String fieldName, Object value) throws IllegalAccessException, NoSuchFieldException {
		Class<?> clazz = obj.getClass();
		do {
			for (Field field : clazz.getDeclaredFields()) {
				if (field.getName().equals(fieldName)) {
					setAccessible(clazz.getDeclaredField(fieldName)).set(obj, value);
					return;
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);
		throw new NoSuchFieldException("Can't find field " + fieldName);
	}

	public static void invokeMethod(Object obj, String methodName, Object... args) throws IllegalAccessException, InvocationTargetException {
		Class<?> clazz = obj.getClass();
		do {
			for (Method method : clazz.getDeclaredMethods()) {
				if (method.getName().equals(methodName) && (method.getParameterTypes().length == args.length)) {
					setAccessible(method).invoke(obj, args);
				}
			}
		} while ((clazz = clazz.getSuperclass()) != null);
	}

}
