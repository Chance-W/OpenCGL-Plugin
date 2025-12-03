package com.opencgl.template.controller;


import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.TreeViewUtil;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.template.dao.TestDao;
import com.opencgl.template.model.Person;
import com.opencgl.template.model.TestDataDto;
import com.opencgl.template.views.TemplateWidgetView;
import io.github.palexdev.materialfx.utils.StringUtils;
import io.github.palexdev.materialfx.utils.others.FunctionalStringConverter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

/**
 * @author Chance.W
 * @version 9.0
 * @className TestController
 * @description TODO
 * @date 2022/8/6 18:27
 */
public class TemplateWidgetController extends TemplateWidgetView implements Initializable, TreeOperateService<TestDataDto> {

    private static final Logger logger = LoggerFactory.getLogger(TemplateWidgetController.class);
    private final TestDao testDao = new TestDao();


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            init();
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    void init()
        throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        List<TestDataDto> testDataDtos = queryAll();
        TreeView<TestDataDto> treeView = TreeViewUtil.buildTreeView(testDataDtos, rootPane, this, TestDataDto.class);
        ThemeSwitchUtil.treeStyleSwitch(rootPane, contentBorderPane, treeView);

        testButton.setOnAction(actionEvent -> {
            Random random = new Random();
            testArea.setText(String.valueOf(random.nextLong()));
        });

        testButton2.setOnAction(event -> {
            String result = DialogUtil.show("ddd");
            logger.info("cccccccccccccc {}", result);
        });

        testButton3.setOnAction(event -> Platform.runLater(() -> LoadingUtil.show(rootPane)));

        testButton4.setOnAction(event -> DialogUtil.showSuccessInfo("HELLO!!!"));

        treeView.getSelectionModel()
            .selectedItemProperty()
            .addListener((observable, oldValue, newValue)
                -> {
                if (newValue != null) {
                    testButton5.setDisable(!newValue.getValue().getIsLeaf());
                }
            });

        testButton5.setOnAction(event -> {
            TestDataDto value = treeView.getSelectionModel().getSelectedItem().getValue();
            CustomizeTreeItem<TestDataDto> update = update(value);
            if (update != null) {
                TooltipUtil.showToast(contentBorderPane, "修改成功");
            }

        });

        StringConverter<Person> converter = FunctionalStringConverter.to(person -> (person == null) ? "" : person.getName() + " " + person.getSurname());
        Function<String, Predicate<Person>> filterFunction = s -> person -> StringUtils.containsIgnoreCase(converter.toString(person), s);

        filterCombo.setItems(buildPersons());
        filterCombo.setConverter(converter);
        filterCombo.setFilterFunction(filterFunction);

        filterCombo.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.A) {
                filterCombo.selectAll();
                event.consume();
            }
            else if (event.isControlDown() && event.getCode() == KeyCode.C) {
                filterCombo.copy();
                event.consume();
            }
            else if (event.isControlDown() && event.getCode() == KeyCode.V) {
                filterCombo.paste();
                event.consume();
            }
        });

    }


    private ObservableList<Person> buildPersons() {
        return FXCollections.observableArrayList(
            Person.ofSplit("Turner Romero", " ").randomAge(),
            Person.ofSplit("Harley Hays", " ").randomAge(),
            Person.ofSplit("Jeffrey Cannon", " ").randomAge(),
            Person.ofSplit("Simeon Huang", " ").randomAge(),
            Person.ofSplit("Jennifer Donovan", " ").randomAge(),
            Person.ofSplit("Hezekiah Stout", " ").randomAge(),
            Person.ofSplit("Roberto Evans", " ").randomAge(),
            Person.ofSplit("Braxton Watts", " ").randomAge(),
            Person.ofSplit("Jayvon Wilkinson", " ").randomAge(),
            Person.ofSplit("Anabelle Chang", " ").randomAge(),
            Person.ofSplit("Abigayle Christensen", " ").randomAge(),
            Person.ofSplit("Fletcher May", " ").randomAge(),
            Person.ofSplit("Marisol Morris", " ").randomAge(),
            Person.ofSplit("Grant Wilson", " ").randomAge(),
            Person.ofSplit("Hayden Baldwin", " ").randomAge(),
            Person.ofSplit("Markus Davidson", " ").randomAge(),
            Person.ofSplit("Madelyn Farmer", " ").randomAge(),
            Person.ofSplit("Deandre Crosby", " ").randomAge(),
            Person.ofSplit("Casey Hardy", " ").randomAge(),
            Person.ofSplit("Carmelo Velazquez", " ").randomAge(),
            Person.ofSplit("Phillip Hays", " ").randomAge(),
            Person.ofSplit("Damari Mcfarland", " ").randomAge(),
            Person.ofSplit("Selina Norton", " ").randomAge(),
            Person.ofSplit("Lukas Vaughan", " ").randomAge(),
            Person.ofSplit("Charlie Carney", " ").randomAge()
        );
    }

    @Override
    public CustomizeTreeItem<TestDataDto> add(TestDataDto treeDataDto) {
        try {
            treeDataDto.setText(testArea.getText());
            Long newId = testDao.inset(treeDataDto);
            treeDataDto.setId(newId);
            return new CustomizeTreeItem<>(treeDataDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<TestDataDto> importData(TestDataDto testDataDto) {
        return null;
    }

   /* @Override
    public CustomizeTreeItem<TestDataDto> addSub(TestDataDto testDataDto) {
        testDataDto.setText(testArea.getText());
        try {
            Long inset = testDao.inset(testDataDto);
            testDataDto.setId(inset);
            return new CustomizeTreeItem<>(testDataDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }*/

    @Override
    public CustomizeTreeItem<TestDataDto> delete(TestDataDto testDataDto) {
        try {
            testDao.delete(testDataDto);
            return new CustomizeTreeItem<>(testDataDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<TestDataDto> update(TestDataDto testDataDto) {
        testDataDto.setText(testArea.getText());
        try {
            testDao.update(testDataDto);
            logger.info("{}", testDataDto);
            return new CustomizeTreeItem<>(testDataDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(TestDataDto TestDataDto) {
        Platform.runLater(() -> testArea.setText(TestDataDto.getText()));
    }

    @Override
    public List<TestDataDto> queryAll() {
        return testDao.queryAllData();
    }
}
