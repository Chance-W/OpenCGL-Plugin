package com.opencgl.plugin.zookeeper.views;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import lombok.Getter;
import lombok.Setter;

/**
 * @ClassName: ZookeeperToolView
 * @Description: Zookeeper工具
 * @author: xufeng
 * @date: 2019/4/8 17:00
 */

@Getter
@Setter
public abstract class ZookeeperToolView implements Initializable {
    @FXML protected javafx.scene.layout.VBox connectionSidebar;
    @FXML protected TextField connectionNameField;
    @FXML protected Button saveConnectionButton;
    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    protected TextField zkServersTextField;
    @FXML
    protected Spinner<Integer> connectionTimeoutSpinner;
    @FXML
    protected Tab nodeDataTab;
    @FXML
    protected Tab nodeMetadataTab;
    @FXML
    protected Tab nodeAclTab;

    @FXML
    protected Label zkServersLabel;
    @FXML
    protected Label connectionTimeoutLabel;

    @FXML
    protected Button connectButton;
    @FXML
    protected Button disconnectButton;
    @FXML
    protected Button refreshButton;
    @FXML
    protected TreeView<String> nodeTreeView;
    @FXML protected TextField nodeSearchField;
    @FXML protected Button searchAllButton;
    @FXML protected Button cancelSearchButton;
    @FXML protected Button clearSearchButton;
    @FXML protected Label searchStatus;
    @FXML protected ProgressIndicator searchProgress;

    @FXML
    protected Button nodeDataSaveButton;
    @FXML
    protected TextArea nodeDataValueTextArea;

    @FXML
    protected Label aclVersionLabel;
    @FXML
    protected Label creationTimeLabel;
    @FXML
    protected Label childrenVersionLabel;
    @FXML
    protected Label creationIdLabel;
    @FXML
    protected Label dataLengthLabel;
    @FXML
    protected Label ephemeralOwnerLabel;
    @FXML
    protected Label lastModifiedTimeLabel;
    @FXML
    protected Label modifiedIdLabel;
    @FXML
    protected Label numChildrenLabel;
    @FXML
    protected Label nodeIdLabel;
    @FXML
    protected Label dataVersionLabel;

    @FXML
    protected Label schemeLabel;
    @FXML
    protected Label idLabel;
    @FXML
    protected Label permissionsLabel;

    @FXML
    protected TextField A_VERSIONTextField;
    @FXML
    protected TextField C_TIMETextField;
    @FXML
    protected TextField C_VERSIONTextField;
    @FXML
    protected TextField CZXIDTextField;
    @FXML
    protected TextField DATA_LENGTHTextField;
    @FXML
    protected TextField EPHEMERAL_OWNERTextField;
    @FXML
    protected TextField M_TIMETextField;
    @FXML
    protected TextField MZXIDTextField;
    @FXML
    protected TextField NUM_CHILDRENTextField;
    @FXML
    protected TextField PZXIDTextField;
    @FXML
    protected TextField VERSIONTextField;
    @FXML
    protected TextField aclSchemeTextField;
    @FXML
    protected TextField aclIdTextField;
    @FXML
    protected TextField aclPermissionsTextField;

    public AnchorPane getMainAnchorPane() {
        return mainAnchorPane;
    }

    public TreeView<String> getNodeTreeView() {
        return nodeTreeView;
    }

    public TextField getZkServersTextField() {
        return zkServersTextField;
    }

    public Spinner<Integer> getConnectionTimeoutSpinner() {
        return connectionTimeoutSpinner;
    }

    public TextArea getNodeDataValueTextArea() {
        return nodeDataValueTextArea;
    }

    public TextField getA_VERSIONTextField() {
        return A_VERSIONTextField;
    }

    public TextField getC_TIMETextField() {
        return C_TIMETextField;
    }

    public TextField getC_VERSIONTextField() {
        return C_VERSIONTextField;
    }

    public TextField getCZXIDTextField() {
        return CZXIDTextField;
    }

    public TextField getDATA_LENGTHTextField() {
        return DATA_LENGTHTextField;
    }

    public TextField getEPHEMERAL_OWNERTextField() {
        return EPHEMERAL_OWNERTextField;
    }

    public TextField getM_TIMETextField() {
        return M_TIMETextField;
    }

    public TextField getMZXIDTextField() {
        return MZXIDTextField;
    }

    public TextField getNUM_CHILDRENTextField() {
        return NUM_CHILDRENTextField;
    }

    public TextField getPZXIDTextField() {
        return PZXIDTextField;
    }

    public TextField getVERSIONTextField() {
        return VERSIONTextField;
    }

    public TextField getAclSchemeTextField() {
        return aclSchemeTextField;
    }

    public TextField getAclIdTextField() {
        return aclIdTextField;
    }

    public TextField getAclPermissionsTextField() {
        return aclPermissionsTextField;
    }
}
