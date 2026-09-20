package com.opencgl.lanmsg.views;

import com.opencgl.lanmsg.core.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

public class LanMessengerView {
    @FXML public StackPane rootPane;
    public final ListView<Peer> userListView=new ListView<>();
    public final ListView<ChatEntry> chatListView=new ListView<>();
    public final TextArea messageInput=new TextArea();
    public final TextField usernameField=new TextField(),portField=new TextField("2425"),tcpPortField=new TextField("2426"),historySearch=new TextField();
    public final ComboBox<NetworkAddresses.Address> ipComboBox=new ComboBox<>();
    public final Button refreshButton=new Button(),manualButton=new Button(),sendButton=new Button(),sendFileButton=new Button(),pasteButton=new Button();
    public final Button startServiceButton=new Button(),stopServiceButton=new Button(),earlierButton=new Button(),clearButton=new Button(),latestButton=new Button();
    public final Label chatTitleLabel=new Label(),statusLabel=new Label(),myInfoLabel=new Label(),userCountLabel=new Label();
}
