package com.opencgl.ftp.utils;


import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.lang.Singleton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextInputControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Chance.W
 */
public class JavaFxViewUtil {
   static Logger log= LoggerFactory.getLogger(JavaFxViewUtil.class);
    /**
     * 设置改变事件监听操作
     */
    public static void setPropertyAddChangeListener(TextInputControl inputControl, Runnable runnable) {
        inputControl.textProperty().addListener((observable, oldValue, newValue) -> {
            setPropertyChangeRun(runnable);
        });
    }
    /**
     * 设置改变事件监听防重复操作
     */
    public static void setPropertyChangeRun(Runnable runnable) {
        if (Singleton.get(TimedCache.class, (long) 2000).get("initiativeChange") != null) {
            return;
        }
        Singleton.get(TimedCache.class, (long) 2000).put("initiativeChange", true);
        runnable.run();
        Singleton.get(TimedCache.class, (long) 2000).remove("initiativeChange");
    }




    /**
     * 设置Spinner最大最小值
     */
    public static void setSpinnerValueFactory(Spinner<Integer> spinner, int min, int max) {
        setSpinnerValueFactory(spinner, min, max, min, 1);
    }

    public static void setSpinnerValueFactory(Spinner<Integer> spinner, int min, int max, int initialValue) {
        setSpinnerValueFactory(spinner, min, max, initialValue, 1);
    }

    public static void setSpinnerValueFactory(Spinner<Double> spinner, double min, double max) {
        setSpinnerValueFactory(spinner, min, max, min, 1d);
    }

    public static void setSpinnerValueFactory(Spinner<Double> spinner, double min, double max, double initialValue) {
        setSpinnerValueFactory(spinner, min, max, initialValue, 1d);
    }

    public static void setSpinnerValueFactory(Spinner spinner, Number min, Number max, Number initialValue, Number amountToStepBy) {
        if (min instanceof Integer) {
            SpinnerValueFactory.IntegerSpinnerValueFactory secondStart_0svf = new SpinnerValueFactory.IntegerSpinnerValueFactory((int) min, (int) max, (int) initialValue, (int) amountToStepBy);
            spinner.setValueFactory(secondStart_0svf);
            spinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
                try {
                    spinner.getValueFactory().setValue(Integer.parseInt(newValue));
                } catch (Exception e) {
                    log.warn("数字int转换异常 newValue:" + newValue);
                    spinner.getEditor().setText(oldValue);
                }
            });
        } else if (min instanceof Double) {
            SpinnerValueFactory.DoubleSpinnerValueFactory secondStart_0svf = new SpinnerValueFactory.DoubleSpinnerValueFactory((double) min, (double) max, (double) initialValue, (double) amountToStepBy);
            spinner.setValueFactory(secondStart_0svf);
            spinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
                try {
                    spinner.getValueFactory().setValue(Double.parseDouble(newValue));
                } catch (Exception e) {
                    log.warn("数字double转换异常 newValue:" + newValue);
                    spinner.getEditor().setText(oldValue);
                }
            });
        }
    }


}
