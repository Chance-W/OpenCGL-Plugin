package com.opencgl.base.utils;


import java.text.ParseException;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class FormatVariableUtil {
    private static final Logger log = LoggerFactory.getLogger(FormatVariableUtil.class);

    public static String format(String string) {
        // YYYYMMDDHHMMSS
        String date = null;
        Long dateLong = 0L;
        //YYYY-MM-DD HH:MM:SS
        String date2 = null;
        //"yyyy-MM-dd"
        String date3 = null;
        //"yyMM"
        String date4 = null;
        //"yyyyMMdd"
        String date5 = null;
        //yyyyMM
        String date6 = null;
        //dd.MM.yyyy HH:mm:ss
        String date7 = null;
        //yyyy
        String date8 = null;

        try {
            //yyyymmddHHmmss
            date = new DateFormatUtil().getDateformatyyyymmddHHmmss();
            //DATE
            dateLong = new DateFormatUtil().getDateFormatDate();
            //${YYYY-MM-DD HH:MM:SS}
            date2 = new DateFormatUtil().getFormat7();
            //"yyyy-MM-dd"
            date3 = new DateFormatUtil().getDate();
            //"yyMM"
            date4 = new DateFormatUtil().getDateYYMM();
            //"yyyyMMdd"
            date5 = new DateFormatUtil().getDateyyyyMMdd();
            //yyyyMM
            date6 = new DateFormatUtil().getYyyymm();
            //dd.MM.yyyy HH:mm:ss
            date7 = new DateFormatUtil().getDateDdmmyy();
            //
            date8 = new DateFormatUtil().getDate();

        }
        catch (ParseException e) {
            e.printStackTrace();
            log.error("", e);

        }

        assert date != null;
        assert date2 != null;
        assert date3 != null;
        assert date4 != null;
        assert date5 != null;
        assert date6 != null;
        assert date7 != null;
        assert date8 != null;


        return string.replace("${YYYYMMDDHHMMSS}", date)
            .replace("${DATE}", dateLong.toString())
            .replace("${YYYY-MM-DD HH:MM:SS}", date2)
            .replace("${YYYY-MM-DD}", date3)
            .replace("${YYMM}", date4)
            .replace("${YYYYMMDD}", date5)
            .replace("${YYYYMM}", date6)
            .replace("${DD.MM.YYYY HH:MM:SS}", date7)
            .replace("${YYYY}", date8)
            .replace("${Random}", getRandom(8))
            .replace("${Random1}", getRandom(1))
            .replace("${Random2}", getRandom(2))
            .replace("${Random3}", getRandom(3))
            .replace("${Random4}", getRandom(4))
            .replace("${Random5}", getRandom(5))
            .replace("${Random6}", getRandom(6))
            .replace("${Random7}", getRandom(7))
            .replace("${Random8}", getRandom(8))
            .replace("${Random9}", getRandom(9))
            .replace("${Random10}", getRandom(10));
    }

    public static String getRandom(int length) {
        StringBuilder val = new StringBuilder();
        Random random = new Random();
        int num = random.nextInt(10);
        if (num != 0) {
            val.append(num);
        }
        else {
            val.append(1);
        }
        for (int i = 0; i < length - 1; i++) {
            val.append(random.nextInt(10));
        }
        return val.toString();
    }
}
