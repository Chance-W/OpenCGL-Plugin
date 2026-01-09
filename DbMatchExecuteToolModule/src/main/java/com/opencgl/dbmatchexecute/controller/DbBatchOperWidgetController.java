package com.opencgl.dbmatchexecute.controller;

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.dbmatchexecute.dao.DbBatchOperationWidgetDao;
import com.opencgl.dbmatchexecute.i18n.I18N;
import com.opencgl.dbmatchexecute.model.DbBatchOperWidgetDto;
import com.opencgl.dbmatchexecute.model.DbBatchOperWidgetTextDto;
import com.opencgl.dbmatchexecute.model.UsualEnum;
import com.opencgl.dbmatchexecute.utils.DbUtil;
import com.opencgl.dbmatchexecute.views.DbBatchOperWidgetView;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class DbBatchOperWidgetController extends DbBatchOperWidgetView implements Initializable {
    private final Logger logger = LoggerFactory.getLogger(DbBatchOperWidgetController.class);
    private final DbBatchOperationWidgetDao dbBatchOperWidgetDao = new DbBatchOperationWidgetDao();

    public void setStyle() {
        Tooltip executeTip = new Tooltip();
        executeTip.textProperty().bind(I18N.getBinding("tooltip.execute"));
        execute.setTooltip(executeTip);
        Tooltip dbInfoTip = new Tooltip();
        dbInfoTip.textProperty().bind(I18N.getBinding("tooltip.db_format"));
        moduleDbInfo.setTooltip(dbInfoTip);
    }

    @SneakyThrows
    public void initData() {
        dbBatchOperWidgetDao.checkTable();
        List<DbBatchOperWidgetTextDto> dbBatchOperWidgetTextDtos = dbBatchOperWidgetDao.queryAllData();
        for (DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto : dbBatchOperWidgetTextDtos) {
            operateCenterVbox.getChildren().add(addHbox(dbBatchOperWidgetTextDto));
        }
    }


    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("begin init DbBatchOperWidgetController");
        setStyle();
        initData();
        initI18n();
    }

    private void initI18n() {
        execute.textProperty().bind(I18N.getBinding("button.execute"));
    }

    public void execute() {
        logger.info("begin build sql");
        List<HBox> hBoxes = operateCenterVbox.getChildren().stream().map(h -> (HBox) h).collect(Collectors.toList());
        List<DbBatchOperWidgetTextDto> dbBatchOperWidgetTextDtos = buildWidgetTextList(hBoxes);
        for (DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto : dbBatchOperWidgetTextDtos) {
            OperationHisRecord.record("begin deal db info is:" + dbBatchOperWidgetTextDto.getModuleDbInfo().split("#")[0]);
            Connection connection = DbUtil.getConnection(dbBatchOperWidgetTextDto.getModuleDbInfo().split("#")[0].replace("udal", "mysql"), dbBatchOperWidgetTextDto.getModuleDbInfo().split("#")[1], dbBatchOperWidgetTextDto.getModuleDbInfo().split("#")[2]);
            try {
                Statement statement = Objects.requireNonNull(connection).createStatement();
                List<String> orginSql;
                try (ResultSet resultSet = statement.executeQuery(dbBatchOperWidgetTextDto.getModuleDbTableName())) {
                    int columnCount = resultSet.getMetaData().getColumnCount();
                    logger.info("get the sql list size is {}", columnCount);
                    orginSql = new ArrayList<>();
                    while (resultSet.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            String sql = resultSet.getString(i);
                            if (dbBatchOperWidgetTextDto.getDelOriginTableCheckBox()) {
                                if (sql.toLowerCase().startsWith("create table") && dbBatchOperWidgetTextDto.getModuleDbInfo().contains("mysql")) {
                                    orginSql.add("DROP TABLE IF EXISTS " + sql.split(" ")[2].replace("(", "").trim());
                                }
                                else {
                                    orginSql.add("DROP TABLE " + sql.split(" ")[2].replace("(", "").trim());
                                }
                            }
                            orginSql.add(sql);
                        }
                    }
                }
                logger.info("end build the sql list are [{}]", orginSql);
                //  StringBuilder targetSql = new StringBuilder();
                String[] suffixs = dbBatchOperWidgetTextDto.getModuleSuffix().split(";");
                List<String> sqlRecords = new ArrayList<>();
                for (String suffix : suffixs) {
                    String suffix1 = suffix.split("-")[0];
                    String suffix2 = suffix.split("-")[1];
                    int suffix3 = Integer.parseInt(suffix.split("-")[2]);
                    List<String> sqls = new ArrayList<>();

                    for (String sql : orginSql) {
                        if (sql.contains(suffix1)) {
                            for (int j = 0; j < suffix3; j++) {
                                // targetSql.append(sql.replace(suffix1, dateFormat(new Date(), suffix2.split("#")[0], suffix2.split("#")[1], j))).append(";\n");
                                String executeSql = sql.replace(suffix1, dateFormat(new Date(), suffix2.split("#")[0], suffix2.split("#")[1], j));

                                logger.info("execute sql is {}", executeSql);

                                if (dbBatchOperWidgetTextDto.getModuleDbInfo().contains("udal")) {
                                    sqlRecords.add(executeSql.toLowerCase() + ";\n");
                                }
                                else {
                                    try {
                                        statement.executeUpdate(executeSql);
                                    }
                                    catch (Exception e) {
                                        logger.error("", e);
                                    }
                                }
                            }
                            sqls.add(sql);
                        }
                    }
                    sqls.forEach(orginSql::remove);
                }
                //记录历史
                OperationHisRecord.record(sqlRecords.toString());
            }
            catch (SQLException e) {
                logger.error("", e);
            }
        }
    }

    public void replaceAll(StringBuilder sb, Pattern pattern, String replacement) {
        Matcher m = pattern.matcher(sb);
        int start = 0;
        while (m.find(start)) {
            sb.replace(m.start(), m.end(), replacement);
            start = m.start() + replacement.length();
        }
    }


    public String dateFormat(Date date, String type, String format, int count) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        switch (type) {
            case UsualEnum.YEAR_MONTH_TYPE, UsualEnum.MONTH_TYPE -> calendar.add(Calendar.MONTH, count);
            case UsualEnum.YEAR_TYPE -> calendar.add(Calendar.YEAR, count);
            case UsualEnum.DAY_TYPE -> calendar.add(Calendar.DATE, count);
            default -> {
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(calendar.getTime());
    }

    public void addItem() {
        logger.info("{}", operateCenterVbox.getChildren().size());
        if (StringUtils.isEmpty(moduleDbInfo.getText()) || StringUtils.isEmpty(moduleDbTableName.getText()) || StringUtils.isEmpty(moduleSuffix.getText())) {
            DialogUtil.showErrorInfo(I18N.get("msg.required_empty"));
            return;
        }
        DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto = DbBatchOperWidgetTextDto.builder().moduleDbInfo(moduleDbInfo.getText()).moduleDbTableName(moduleDbTableName.getText()).moduleSuffix(moduleSuffix.getText()).build();
        try {
            if (dbBatchOperWidgetDao.queryData(dbBatchOperWidgetTextDto) > 0) {
                DialogUtil.showErrorInfo(I18N.get("msg.duplicate"));
                return;
            }
            operateCenterVbox.getChildren().add(0, addHbox(dbBatchOperWidgetTextDto));
            dbBatchOperWidgetDao.insertData(dbBatchOperWidgetTextDto);
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }

    public HBox addHbox(DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto) {
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setSpacing(3);

        HBox.setMargin(operateCenterVbox, new Insets(10, 10, 0, 0));
        hBox.setPadding(new Insets(10, 0, 0, 10));
        MFXCheckbox chooseCheckBox = new MFXCheckbox();
        Tooltip selectTip = new Tooltip();
        selectTip.textProperty().bind(I18N.getBinding("tooltip.select_execute"));
        chooseCheckBox.setTooltip(selectTip);


        MFXCheckbox delOriginTableCheckBox = new MFXCheckbox();
        Tooltip delOriginTip = new Tooltip();
        delOriginTip.textProperty().bind(I18N.getBinding("tooltip.del_origin_table"));
        delOriginTableCheckBox.setTooltip(delOriginTip);

        MFXTextField moduleDbInfoTemplate = getMfxTextField(dbBatchOperWidgetTextDto);

        MFXTextField moduleDbTableNameTemplate = new MFXTextField();
        moduleDbTableNameTemplate.setEditable(false);
        // moduleDbTableNameTemplate.setPrefHeight(30);
        moduleDbTableNameTemplate.setPrefWidth(50.0);
        moduleDbTableNameTemplate.setMaxWidth(50.0);
        moduleDbTableNameTemplate.setStyle("-fx-prompt-text-fill:rgba(63,75,77,0.39)");
        moduleDbTableNameTemplate.setText(dbBatchOperWidgetTextDto.getModuleDbTableName());
        MFXTextField moduleSuffixTemplate = new MFXTextField();
        moduleSuffixTemplate.setEditable(false);
        moduleSuffixTemplate.setMaxWidth(30);
        moduleSuffixTemplate.setPrefWidth(30.0);
        moduleSuffixTemplate.setStyle("-fx-prompt-text-fill:rgba(63,75,77,0.39)");

        moduleSuffixTemplate.setText(dbBatchOperWidgetTextDto.getModuleSuffix());
        MFXButton delTemplate = new MFXButton();
        delTemplate.setPrefHeight(30);
        delTemplate.setText(I18N.get("button.del"));

        hBox.getChildren().addAll(chooseCheckBox, delOriginTableCheckBox, moduleDbInfoTemplate, moduleDbTableNameTemplate, moduleSuffixTemplate, delTemplate);

        /*删除条目*/
        delTemplate.setOnMouseClicked(mouseEvent -> {
            DbBatchOperWidgetDto dbBatchOperWidgetDto = buildWidget(((HBox) delTemplate.getParent()).getChildren());
            operateCenterVbox.getChildren().remove(delTemplate.getParent());
            try {
                dbBatchOperWidgetDao.delLevelData(dbBatchOperWidgetDto.buildWidgetText());
            }
            catch (Exception e) {
                logger.error("", e);
            }
        });
        return hBox;
    }

    private static MFXTextField getMfxTextField(DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto) {
        MFXTextField moduleDbInfoTemplate = new MFXTextField();
        moduleDbInfoTemplate.setEditable(false);

        //moduleDbInfoTemplate.setPrefHeight(30);
        moduleDbInfoTemplate.setPrefWidth(70);
        moduleDbInfoTemplate.setMaxWidth(100);
        //"-fx-prompt-text-fill:rgba(63,75,77,0.39)" unFocusColor="#4d4d4d35"
        moduleDbInfoTemplate.setStyle("-fx-prompt-text-fill:rgba(63,75,77,0.39)");
        moduleDbInfoTemplate.setText(dbBatchOperWidgetTextDto.getModuleDbInfo());
        Tooltip t = new Tooltip();
        t.setText(dbBatchOperWidgetTextDto.getModuleDbInfo());
        moduleDbInfoTemplate.setTooltip(t);
        return moduleDbInfoTemplate;
    }


    public List<DbBatchOperWidgetDto> buildWidgetList(List<HBox> hBoxList) {
        List<DbBatchOperWidgetDto> dbBatchOperWidgetDtos = new ArrayList<>();
        for (HBox hBox : hBoxList) {
            dbBatchOperWidgetDtos.add(buildWidget(hBox.getChildren()));
        }
        return dbBatchOperWidgetDtos;
    }

    public List<DbBatchOperWidgetTextDto> buildWidgetTextList(List<HBox> hBoxList) {
        List<DbBatchOperWidgetTextDto> dbBatchOperWidgetTextDtos = new ArrayList<>();
        for (HBox hBox : hBoxList) {
            DbBatchOperWidgetTextDto dbBatchOperWidgetTextDto = buildWidget(hBox.getChildren()).buildWidgetText();
          // if (dbBatchOperWidgetTextDto.getChecked()) {
                dbBatchOperWidgetTextDtos.add(dbBatchOperWidgetTextDto);
          //  }
        }
        logger.info("get the checked widget List is {}", dbBatchOperWidgetTextDtos);
        return dbBatchOperWidgetTextDtos;
    }


    public DbBatchOperWidgetDto buildWidget(List<Node> list) {
        return DbBatchOperWidgetDto.builder().
            mfxCheckBox((MFXCheckbox) list.get(0))
            .delOriginTableCheckBox((MFXCheckbox) list.get(1))
            .moduleDbInfoTemplate((MFXTextField) list.get(2))
            .moduleDbTableNameTemplate((MFXTextField) list.get(3))
            .moduleSuffixTemplate((MFXTextField) list.get(4))
            .build();
    }


}
