package com.opencgl.controller;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dao.RedisWidgetDao;
import com.opencgl.model.RedisWidgetDto;
import com.opencgl.utils.RedisClusterUtil;
import com.opencgl.views.RedisWidgetView;
import javafx.fxml.Initializable;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 * @date 2020/4/14-21:18
 */
public class RedisWidgetController extends RedisWidgetView implements Initializable, TreeOperateService<RedisWidgetDto> {
    private static final Logger log = LoggerFactory.getLogger(RedisWidgetController.class);
    private RedisClusterUtil redisClusterUtil;
    private final RedisWidgetDao redisWidgetDao = new RedisWidgetDao();

    public void init() {
        connectButton.setOnAction(event -> {
            if (StringUtils.isEmpty(clusterRedisAdress.getText())) {
                DialogUtil.showErrorInfo("集群地址不能为空", mainAnchorPane);
            }
            else {
                try {
                    redisClusterUtil = new RedisClusterUtil(clusterRedisAdress.getText());
                    if (redisClusterUtil.queryConnState()) {
                        resultDetail.setText("连接成功!");
                    }
                    else {
                        resultDetail.setText("连接失败!,请检查日志");
                    }
                }
                catch (Exception e) {
                    log.error("{}", Arrays.toString(e.getStackTrace()));
                    DialogUtil.showErrorInfo("连接异常,请检查控制台日志", mainAnchorPane);
                }
            }
        });

        disconnectButton.setOnAction(event -> {
            try {
                if (null == redisClusterUtil || !redisClusterUtil.queryConnState()) {
                    DialogUtil.showErrorInfo("Redis未连接,无需断开", mainAnchorPane);
                    return;
                }
                redisClusterUtil.closeJedis();
                if (!redisClusterUtil.queryConnState()) {
                    resultDetail.setText("断开成功!");
                }
            }
            catch (Exception e) {
                log.error("{}", Arrays.toString(e.getStackTrace()));
                DialogUtil.showErrorInfo("断开异常,请检查控制台日志", mainAnchorPane);
            }
        });

        queryKeyValueButton.setOnAction(event -> {
            if (StringUtils.isEmpty(keypattern.getText())) {
                DialogUtil.showErrorInfo("Key值不能为空,请配置正确的值", mainAnchorPane);
            }
            else if (null == redisClusterUtil || !redisClusterUtil.queryConnState()) {
                DialogUtil.showErrorInfo("Redis未连接,请连接后重试", mainAnchorPane);
            }
            else {
                resultDetail.setText(redisClusterUtil.queryAppointKey(keypattern.getText()));
            }
        });

        deleteKeyButton.setOnAction(event -> {
            if (StringUtils.isEmpty(keypattern.getText())) {
                DialogUtil.showErrorInfo("Key值不能为空,请配置正确的值", mainAnchorPane);
            }
            else if (null == redisClusterUtil || !redisClusterUtil.queryConnState()) {
                DialogUtil.showErrorInfo("Redis未连接,请连接后重试", mainAnchorPane);
            }
            else {
                resultDetail.setText(redisClusterUtil.delAppoinKey(keypattern.getText()));
            }
        });

        queryPatternKeyButton.setOnAction(event -> {
            if (StringUtils.isEmpty(keypattern.getText())) {
                DialogUtil.showErrorInfo("Key值不能为空,请配置正确的值", mainAnchorPane);
            }
            else if (null == redisClusterUtil || !redisClusterUtil.queryConnState()) {
                DialogUtil.showErrorInfo("Redis未连接,请连接后重试", mainAnchorPane);
            }
            else {
                resultDetail.setText(null);
                List<Set<String>> objects = redisClusterUtil.queryAllKey(keypattern.getText());
                for (Set<String> object : objects) {
                    for (String key : object) {
                        resultDetail.appendText(key + "\n");
                    }

                }
            }
        });


        setKeyAndValueButton.setOnAction(event -> {
            if (StringUtils.isEmpty(setKey.getText()) || StringUtils.isEmpty(setValue.getText())) {
                DialogUtil.showErrorInfo("Key或value均不能为空,请配置正确的值", mainAnchorPane);
            }
            else if (null == redisClusterUtil || !redisClusterUtil.queryConnState()) {
                DialogUtil.showErrorInfo("Redis未连接,请连接后重试", mainAnchorPane);
            }
            else {
                redisClusterUtil.updateAppoinKey(setKey.getText(), setValue.getText());
                if (redisClusterUtil.queryAppointKey(setKey.getText()).equals(setValue.getText())) {
                    resultDetail.setText(setKey.getText() + "设置成功! " + "值等于" + setValue.getText());
                }
            }
        });


        hgetButton.setOnAction(event -> {
            if (StringUtils.isEmpty(hashKey.getText())) {
                DialogUtil.showErrorInfo("HashKey值不能为空!", mainAnchorPane);
            }
            else if (null == redisClusterUtil || !redisClusterUtil.queryConnState()) {
                DialogUtil.showErrorInfo("Redis未连接,请连接后重试", mainAnchorPane);
            }
            else {
                resultDetail.clear();
                String[] strings = redisClusterUtil.hgetAll(hashKey.getText()).toString().split(",");
                for (String string : strings) {
                    resultDetail.appendText(string + "\n");
                }
            }
        });

        hsetButton.setOnAction(event -> {
            if (StringUtils.isEmpty(hashKey.getText()) && StringUtils.isEmpty(hashMapKey.getText()) && StringUtils.isEmpty(hashMapValue.getText())) {
                DialogUtil.showErrorInfo("使用hset时,mapKey和mapValue均不能为空");
            }
            else {
                Map<String, String> map = new HashMap<>(32);
                map.put(hashMapKey.getText(), hashMapValue.getText());
                Long size = redisClusterUtil.hset(hashKey.getText(), map);
                if (size > 0) {
                    resultDetail.setText(hashKey.getText() + ":" + hashMapKey.getText() + "成功设置为" + hashMapValue.getText());
                }
                else {
                    resultDetail.setText("设置失败");
                }

            }
        });

        hdelButton.setOnAction(event -> {
            if (StringUtils.isEmpty(hashKey.getText()) && StringUtils.isEmpty(hashMapKey.getText())) {
                DialogUtil.showErrorInfo("Key和mapKey不能为空", mainAnchorPane);
            }
            else {
                Long size = redisClusterUtil.hdel(hashKey.getText(), hashMapKey.getText());
                if (size > 0) {
                    resultDetail.setText(hashKey.getText() + "." + hashMapKey.getText() + "删除成功");
                }
                else {
                    resultDetail.setText("设置失败");
                }
            }
        });
    }

    @SneakyThrows
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        redisWidgetDao.checkTable();
        init();
    }


    @Override
    public CustomizeTreeItem<RedisWidgetDto> add(RedisWidgetDto treeDataDto) {
        return null;
    }

    @Override
    public CustomizeTreeItem<RedisWidgetDto> importData(RedisWidgetDto treeDataDto) {
        return null;
    }

    @Override
    public CustomizeTreeItem<RedisWidgetDto> delete(RedisWidgetDto treeDataDto) {
        return null;
    }

    @Override
    public CustomizeTreeItem<RedisWidgetDto> update(RedisWidgetDto treeDataDto) {
        return null;
    }

    @Override
    public void changeToDisplay(RedisWidgetDto treeDataDto) {

    }

    @Override
    public List<RedisWidgetDto> queryAll() {
        return null;
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }
}
