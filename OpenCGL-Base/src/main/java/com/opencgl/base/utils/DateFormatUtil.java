package com.opencgl.base.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DateFormatUtil {
    private final Logger log = LoggerFactory.getLogger(DateFormatUtil.class);
    private String expireDate;
    private String reserveDate;
    private String dateYYMM;
    private String date;
    private String dateyyyyMMdd;
    private String dateDdmmyy;
    private String dateBcFormat2;
    private String dateformatyyyymmddHHmmss;
    private String yyyymm;
    private String yyyy;
    private String yYMMDD;
    private Long dateFormatDate;
    private String format7;
    private String format8;
    private String newExpireDate;
    private String newReserveDate;


    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf2 = new SimpleDateFormat("yyMM");
    SimpleDateFormat sdf3 = new SimpleDateFormat("yyyyMMdd");
    SimpleDateFormat sdf4 = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    SimpleDateFormat sdf5 = new SimpleDateFormat("yyyyMMddHHmmss");
    SimpleDateFormat sdf6 = new SimpleDateFormat("yyyyMM");
    SimpleDateFormat sdf7 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat sdf8 = new SimpleDateFormat("yyMMdd");
    SimpleDateFormat sdf9 = new SimpleDateFormat("yyyy");

    public String getFormat8() {
        return format8;
    }

    public void setFormat8(String format8) {
        this.format8 = format8;
    }


    public String getFormat7() {
        return format7;
    }

    public void setFormat7(String format7) {
        this.format7 = format7;
    }


    public String getNewExpireDate() {
        return newExpireDate;
    }

    public String getNewReserveDate() {
        return newReserveDate;
    }


    public String getYyyymm() {
        log.info("get the yyyymm is:" + yyyymm);
        return yyyymm;
    }

    public String getYyyy() {
        log.info("get the yyyy is:" + yyyy);
        return yyyy;
    }

    public String getDateformatyyyymmddHHmmss() {
        log.info("get the dateformatyyyymmddHHmmss is:" + dateformatyyyymmddHHmmss);
        return dateformatyyyymmddHHmmss;
    }

    public Long getDateFormatDate() {
        log.info("get the DateFormatDate is:" + dateFormatDate);
        return dateFormatDate;
    }

    public String getExpireDate() {
        log.info("get the expireDate is:" + expireDate);
        return expireDate;
    }

    private String getReserveDate() {
        log.info("get the reserveDate is:" + reserveDate);
        return reserveDate;
    }

    public String getDateYYMM() {
        log.info("get the dateYYMM is:" + dateYYMM);
        return dateYYMM;
    }

    public String getDate() {
        log.info("get the date is:" + date);
        return date;
    }

    public String getDateYYMMDD() {
        log.info("get the dateYYMMDD is:" + yYMMDD);
        return yYMMDD;
    }

    public String getDateyyyyMMdd() {
        log.info("get the dateyyyyMMdd is:" + dateyyyyMMdd);
        return dateyyyyMMdd;
    }

    public String getDateDdmmyy() {
        log.info("get the dateDdmmyy is:" + dateDdmmyy);
        return dateDdmmyy;
    }

    public String getDateBcFormat2() {
        log.info("get the dateBcFormat2 is:" + dateBcFormat2);
        return dateBcFormat2;
    }

    //时间获取
    public DateFormatUtil() throws ParseException {
        formatDate(new Date());
    }


    public DateFormatUtil(Date date) throws ParseException {
        formatDate(date);
    }

    private void formatDate(Date inputDate) throws ParseException {
        dateFormatDate = inputDate.getTime();
        dateYYMM = sdf2.format(inputDate);
        yyyymm = sdf6.format(inputDate);
        yyyy = sdf9.format(inputDate);
        yYMMDD = sdf8.format(inputDate);
        date = sdf.format(inputDate);
        dateyyyyMMdd = sdf3.format(inputDate);
        dateDdmmyy = sdf4.format(inputDate);
        dateformatyyyymmddHHmmss = sdf5.format(inputDate);
        format7 = sdf7.format(inputDate);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(inputDate);
        calendar.add(Calendar.DATE, 1);
        format8 = sdf7.format(calendar.getTime());
        Date dt = sdf.parse(date);
        Calendar rightNow = Calendar.getInstance();
        rightNow.setTime(dt);
        rightNow.add(Calendar.MONTH, 1);
        Date dt1 = rightNow.getTime();
        expireDate = sdf.format(dt1);
        rightNow.add(Calendar.DATE, 1);
        newExpireDate = sdf.format(dt1);
        rightNow.add(Calendar.MONTH, 1);
        Date dt2 = rightNow.getTime();
        reserveDate = sdf.format(dt2);
        rightNow.add(Calendar.DATE, 1);
        newReserveDate = sdf.format(dt2);
        dateBcFormat2 = sdf4.format(dt2);
    }

    public Long getDate(String timeString, String format) {
        //字符串转时间戳
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        Long date = null;
        try {
            date = sdf.parse(timeString).getTime() / 1000L;
        } catch (ParseException e) {
            e.printStackTrace();
            log.error("",e);
        }
        return date;
    }
}
