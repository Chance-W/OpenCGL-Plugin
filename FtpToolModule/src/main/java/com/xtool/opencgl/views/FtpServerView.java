package com.xtool.opencgl.views;

import com.xtool.opencgl.model.FtpServerTableBean;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Chance.W
 */
@Getter
@Setter
public abstract class FtpServerView implements Initializable {
	@FXML
	private AnchorPane mainAnchorPane;
	@FXML
	protected TextField userNameTextField;
	@FXML
	protected TextField passwordTextField;
	@FXML
	protected TextField homeDirectoryTextField;
	@FXML
	protected Button chooseHomeDirectoryButton;
	@FXML
	protected CheckBox downFileCheckBox;
	@FXML
	protected CheckBox upFileCheckBox;
	@FXML
	protected CheckBox deleteFileCheckBox;
	@FXML
	protected Button buttonAddItem;
	@FXML
	protected TextField portTextField;
	@FXML
	protected CheckBox anonymousLoginEnabledCheckBox;
	@FXML
	protected TextField anonymousLoginEnabledTextField;
	@FXML
	protected Button anonymousLoginEnabledButton;
	@FXML
	protected Spinner<Integer> maxConnectCountSpinner;
	@FXML
	protected Button startButton;
	@FXML
	protected TableView<FtpServerTableBean> tableViewMain;
	@FXML
	protected TableColumn<FtpServerTableBean, Boolean> isEnabledTableColumn;
	@FXML
	protected TableColumn<FtpServerTableBean, String> userNameTableColumn;
	@FXML
	protected TableColumn<FtpServerTableBean, String> passwordTableColumn;
	@FXML
	protected TableColumn<FtpServerTableBean, String> homeDirectoryTableColumn;
	@FXML
	protected TableColumn<FtpServerTableBean, Boolean> downFIleTableColumn;
	@FXML
	protected TableColumn<FtpServerTableBean, Boolean> upFileTableColumn;
	@FXML
	protected TableColumn<FtpServerTableBean, Boolean> deleteFileTableColumn;

}