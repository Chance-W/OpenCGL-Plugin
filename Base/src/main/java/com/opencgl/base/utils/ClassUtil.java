package com.opencgl.base.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Chance.W
 */
public class ClassUtil {

    private static List<Field> getAllFields(Class request) {
        List<Field> fieldList = new ArrayList<>();
        Class tempClass = request;
        while (null != tempClass) {
            fieldList.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            tempClass = tempClass.getSuperclass();
        }
        return fieldList;
    }

    public static Map<String, Field> getAllFieldMap(Class request) {
        List<Field> fieldList = getAllFields(request);

        return fieldList.stream()
                .collect(Collectors.toMap(field -> field.getName().toLowerCase(), a -> a));
    }


}
