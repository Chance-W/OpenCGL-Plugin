package com.opencgl.base.utils;

import java.text.SimpleDateFormat;

public class ValidateUtil {

    public static Boolean isTimeStap(Object date) {
        if (date instanceof String) {
            if (date.toString().endsWith("0") & (date.toString().length() == 26 || date.toString().length() == 20)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                try {
                    sdf.parse(date.toString());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return false;
    }

    public static Boolean isNumber(Object param){
        return param instanceof Long;

    }
}
