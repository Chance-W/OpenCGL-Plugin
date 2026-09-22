package com.opencgl.lanmsg.controller;

import com.opencgl.lanmsg.core.*;
import com.opencgl.lanmsg.security.*;
import com.opencgl.lanmsg.i18n.I18N;
import com.opencgl.lanmsg.ui.*;
import com.opencgl.lanmsg.views.LanMessengerView;
import com.opencgl.base.theme.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.Initializable;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** FX-only view state; disk, database and networking work live on bounded background queues. */
public class LanMessengerController extends LanMessengerView implements Initializable {
    private final Path data;
    private final Path database;
    private final SecretStore secrets;
    private final ThreadPoolExecutor tasks=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"lan-ui-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final ThreadPoolExecutor controls=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"lan-control");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final CompletableFuture<Void> ready=new CompletableFuture<>();
    private final PauseTransition searchDelay=new PauseTransition(Duration.millis(250));
    private final Map<String,Integer> unread=new HashMap<>();
    private final Map<String,LanService.Progress> progress=new HashMap<>();
    private final LinkedHashMap<String,Image> thumbnails=new LinkedHashMap<>(64,.75f,true);
    private final Set<String> thumbnailPending=new HashSet<>(),thumbnailFailed=new HashSet<>();
    private final Set<String> seenEvents=new LinkedHashSet<>();
    private record Update(long revision,ChatEntry entry) {}
    private final LinkedHashMap<String,Update> liveUpdates=new LinkedHashMap<>();
    private volatile ChatRepository repository;
    private volatile LanService service;
    private volatile LocalVault vault;
    private volatile EncryptedImageCache imageCache;
    private volatile boolean closed;
    private Peer selected;
    private long historyGeneration,messageRevision,cacheGeneration;
    private volatile long serviceGeneration;
    private final Object cachePublication=new Object();
    private boolean composing,busy,historyLoading,sending;
    private final DateTimeFormatter clock=DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public LanMessengerController(){this(Path.of(System.getProperty("user.home"),".opencgl","lan-messenger"));}
    public LanMessengerController(Path data){this(data,(SecretStore)null);}
    public LanMessengerController(Path data,SecretStore secrets){this.data=data;this.database=data.resolve("secure-history.db");this.secrets=secrets;}
    public CompletableFuture<Void> ready(){return ready;}
    private static String tr(String zh,String en){return "zh".equals(I18N.getLocale().getLanguage())?zh:en;}
    private static void caption(Button button,String zh,String en){button.setText(tr(zh,en));}
    private static Label label(String zh,String en){return new Label(tr(zh,en));}
    private void fx(Runnable action){Platform.runLater(()->{if(!closed)action.run();});}
    private <T> void work(Callable<T> action,Consumer<T> success){
        submit(tasks,action,success,this::showFailure);
    }
    private <T> void submit(Executor executor,Callable<T> action,Consumer<T> success,Consumer<Throwable> failure){
        if(closed)return;
        try{executor.execute(()->{try{T value=action.call();fx(()->success.accept(value));}catch(Exception e){fx(()->failure.accept(e));}});}
        catch(RejectedExecutionException e){failure.accept(new IOException(tr("任务队列已满，请稍后重试","Task queue full; try again later")));}
    }
    private void status(String text){statusLabel.setText(text);}
    private void showFailure(Throwable failure){
        Throwable cause=failure;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
        status(tr("操作失败：","Failed: ")+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()));
    }

    @Override public void initialize(URL location,ResourceBundle resources){
        buildLayout();
        userListView.setCellFactory(v->new ListCell<>(){
            @Override protected void updateItem(Peer p,boolean empty){super.updateItem(p,empty);setText(empty||p==null?null:(p.online()?"● ":"○ ")+p.name()+"  "+unread.getOrDefault(p.id(),0)+"\n"+p.ip()+":"+p.port());}
        });
        userListView.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            if(b==null)return;boolean changed=selected==null||!selected.id().equals(b.id());selected=b;unread.remove(b.id());userListView.refresh();
            chatTitleLabel.setText(b.toString());updateButtons();if(changed){chatListView.getItems().clear();loadHistory(false);}
        });
        chatListView.setCellFactory(v->new MessageCell());
        startServiceButton.setOnAction(e->start());
        stopServiceButton.setOnAction(e->stop());
        refreshButton.setOnAction(e->{LanService current=service;work(()->{current.refresh();return null;},v->status(tr("已发送发现请求","Discovery requested")));});
        manualButton.setOnAction(e->manualConnect());
        var pairItem=new MenuItem(tr("连接 / 核对设备","Connect / verify device"));
        pairItem.setOnAction(e->{Peer peer=selected;LanService current=service;if(peer!=null&&current!=null&&current.running())
            work(()->current.connectPeer(peer.ip(),peer.port()),p->{peerEvent(p);updateButtons();});});
        var revokeItem=new MenuItem(tr("撤销设备信任","Revoke device trust"));
        revokeItem.setOnAction(e->{Peer peer=selected;LanService current=service;if(peer==null||current==null||!current.running())return;
            if(confirm(tr("撤销信任并中断该设备传输？","Revoke trust and cancel this device's transfers?"),new Label(peer.name())))
                work(()->{current.revokeTrust(peer.id());return null;},v->{updateButtons();status(tr("信任已撤销；再次连接需双方核对","Trust revoked; verify again on both devices"));});});
        userListView.setContextMenu(new ContextMenu(pairItem,revokeItem));
        sendButton.setOnAction(e->send());
        sendFileButton.setOnAction(e->{var chooser=new FileChooser();var files=chooser.showOpenMultipleDialog(owner());if(files!=null)offerFiles(files);});
        pasteButton.setOnAction(e->pasteImage());
        earlierButton.setOnAction(e->loadHistory(true));
        latestButton.setOnAction(e->{historySearch.clear();loadHistory(false);});
        clearButton.setOnAction(e->clearHistory());
        historySearch.textProperty().addListener((o,a,b)->searchDelay.playFromStart());
        searchDelay.setOnFinished(e->loadHistory(false));
        messageInput.addEventHandler(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            e->composing=e.getComposed()!=null&&!e.getComposed().isEmpty());
        messageInput.addEventFilter(KeyEvent.KEY_PRESSED,e->{
            if(e.isShortcutDown()&&e.getCode()==KeyCode.V&&Clipboard.getSystemClipboard().hasImage()){e.consume();pasteImage();}
            else if(e.getCode()==KeyCode.ENTER&&ComposerInputPolicy.shouldSendOnEnter(composing,e.isShiftDown(),messageInput.getText())){e.consume();send();}
        });
        rootPane.setOnDragOver(e->{if(e.getDragboard().hasFiles()&&selected!=null&&service!=null&&service.running())e.acceptTransferModes(TransferMode.COPY);e.consume();});
        rootPane.setOnDragDropped(e->{boolean ok=e.getDragboard().hasFiles()&&selected!=null&&service!=null&&service.running();if(ok)offerFiles(e.getDragboard().getFiles());e.setDropCompleted(ok);e.consume();});
        updateButtons();status(tr("正在读取本地记录…","Loading local history…"));
        try{tasks.execute(()->{
            LocalVault opened=null;
            try{
                if(!Files.exists(data.resolve("security/vault.check"),LinkOption.NOFOLLOW_LINKS)
                        &&(Files.exists(database,LinkOption.NOFOLLOW_LINKS)||Files.exists(data.resolve("encrypted-images"),LinkOption.NOFOLLOW_LINKS)))
                    throw new IOException("Encrypted data exists but its vault manifest is missing; refusing to create a replacement key");
                opened=secrets==null?LocalVault.openSystem(data.resolve("security")):LocalVault.open(data.resolve("security"),secrets);
                var repo=new ChatRepository(database,opened);repo.markInterrupted();
                var cache=new EncryptedImageCache(data.resolve("encrypted-images"),opened);
                var peers=repo.peers();var addresses=NetworkAddresses.list();
                String name=repo.setting("name",System.getProperty("user.name","OpenCGL")),ip=repo.setting("ip","");
                String udp=repo.setting("udp","2425"),tcp=repo.setting("tcp","2426");
                synchronized(this){if(closed){opened.close();return;}vault=opened;imageCache=cache;repository=repo;opened=null;}
                fx(()->{
                    usernameField.setText(name);portField.setText(udp);tcpPortField.setText(tcp);
                    ipComboBox.getItems().setAll(addresses);ipComboBox.setValue(addresses.stream().filter(a->a.ip().equals(ip)).findFirst().orElse(addresses.isEmpty()?null:addresses.get(0)));
                    userListView.getItems().setAll(peers);updatePeerCount();updateButtons();status(tr("就绪；请选择网卡并启动服务","Ready; select an interface and start"));ready.complete(null);
                });
            }catch(Throwable e){if(opened!=null)try{opened.close();}catch(IOException close){e.addSuppressed(close);}ready.completeExceptionally(e);fx(()->showFailure(e));}
        });}catch(Exception e){ready.completeExceptionally(e);}
    }

    private void buildLayout(){
        caption(startServiceButton,"启动服务","Start");caption(stopServiceButton,"停止服务","Stop");
        caption(refreshButton,"刷新发现","Discover");caption(manualButton,"手动连接","Connect IP");
        caption(sendButton,"发送","Send");caption(sendFileButton,"发送文件","Files");caption(pasteButton,"粘贴图片","Paste image");
        caption(earlierButton,"更早记录","Earlier");caption(clearButton,"清空记录","Clear history");caption(latestButton,"最新消息","Latest");
        sendButton.getStyleClass().add("primary");startServiceButton.getStyleClass().add("primary");
        historySearch.setPromptText(tr("搜索当前会话…","Search conversation…"));
        messageInput.setPromptText(tr("Enter 发送，Shift+Enter 换行；支持拖入多个文件、粘贴图片","Enter to send, Shift+Enter for newline; drop files or paste an image"));
        messageInput.setPrefRowCount(3);messageInput.setWrapText(true);
        chatTitleLabel.setText(tr("选择联系人","Select a peer"));chatTitleLabel.getStyleClass().add("title");
        ipComboBox.setMaxWidth(Double.MAX_VALUE);
        var sidebar=new VBox(8,userCountLabel,userListView,new HBox(8,refreshButton,manualButton),new Separator(),
            label("设备名称","Device name"),usernameField,label("网卡 / 本机 IPv4","Interface / local IPv4"),ipComboBox,
            label("发现端口 UDP","Discovery UDP port"),portField,label("消息 / 文件 TCP 端口","Messages / files TCP port"),tcpPortField,
            new HBox(8,startServiceButton,stopServiceButton));
        sidebar.setPadding(new Insets(12));sidebar.setPrefWidth(280);sidebar.setMinWidth(220);VBox.setVgrow(userListView,Priority.ALWAYS);
        var search=new HBox(8,historySearch,earlierButton,latestButton,clearButton);HBox.setHgrow(historySearch,Priority.ALWAYS);
        var cacheButton=new Button(tr("清理截图缓存","Clear pasted images"));cacheButton.setOnAction(e->clearClipboardCache());
        var actions=new HBox(8,sendFileButton,pasteButton,cacheButton,new Region(),sendButton);HBox.setHgrow(actions.getChildren().get(3),Priority.ALWAYS);
        var chat=new VBox(8,chatTitleLabel,search,chatListView,messageInput,actions);chat.setPadding(new Insets(12));VBox.setVgrow(chatListView,Priority.ALWAYS);
        var split=new SplitPane(sidebar,chat);split.setDividerPositions(.25);
        var warning=label("TLS 1.3 传输；首次连接需双方核对。历史和粘贴缓存加密保存；主动接收/另存的文件及接收临时文件为普通文件。文件 ≤ 1 GiB，粘贴 PNG ≤ 16 MiB。","TLS 1.3; verify first connection on both devices. History and pasted images are encrypted. Received/exported files and receive temporaries are ordinary files. Files ≤ 1 GiB; pasted PNG ≤ 16 MiB.");
        warning.setWrapText(true);warning.getStyleClass().add("muted");
        var footer=new VBox(4,warning,new HBox(12,myInfoLabel,statusLabel));footer.setPadding(new Insets(8,12,8,12));statusLabel.setWrapText(true);
        var layout=new BorderPane(split);layout.setBottom(footer);rootPane.getChildren().setAll(layout);
    }
    private Window owner(){return rootPane.getScene()==null?null:rootPane.getScene().getWindow();}
    private void updateButtons(){
        boolean running=service!=null&&service.running();
        startServiceButton.setDisable(repository==null||running||busy);stopServiceButton.setDisable(!running||busy);
        usernameField.setDisable(running||busy);portField.setDisable(running||busy);tcpPortField.setDisable(running||busy);ipComboBox.setDisable(running||busy);
        refreshButton.setDisable(!running);manualButton.setDisable(!running);
        boolean verified=running&&selected!=null&&service.paired(selected.id());
        sendButton.setTooltip(new Tooltip(verified?tr("发送消息","Send message"):tr("请先右键联系人，选择“连接 / 核对设备”","Right-click the peer and choose Connect / verify device first")));
        sendButton.setDisable(!verified||busy||sending);sendFileButton.setDisable(!verified||busy);pasteButton.setDisable(sendFileButton.isDisabled());
        earlierButton.setDisable(selected==null||historyLoading);clearButton.setDisable(selected==null||running);latestButton.setDisable(selected==null);
        clearButton.setTooltip(new Tooltip(tr("停止服务后可清空记录，避免活动传输恢复记录","Stop the service before clearing history")));
    }
    private void start(){
        if(busy||repository==null)return;
        String name=usernameField.getText().trim();var address=ipComboBox.getValue();
        int udp,tcp;
        try{udp=Integer.parseInt(portField.getText().trim());tcp=Integer.parseInt(tcpPortField.getText().trim());if(udp<1||udp>65535||tcp<1||tcp>65535||name.isBlank()||name.length()>256||address==null)throw new IllegalArgumentException();}
        catch(Exception ex){notifyUser(tr("请填写设备名称、有效网卡和 1–65535 的端口。","Enter a device name, interface and ports between 1 and 65535."));return;}
        busy=true;long generation=++serviceGeneration;updateButtons();
        submit(controls,()->{
            LocalVault currentVault=Objects.requireNonNull(vault,"Encrypted storage is not ready");
            var secure=new SecureTransport(DeviceIdentity.loadOrCreate(currentVault,repository.identity()),new TrustRepository(currentVault),name);
            secure.onPairing(request->Platform.runLater(()->showPairing(request,generation)));
            LanService next=new LanService(repository,name,secure,imageCache);
            next.onPeer(p->fx(()->{if(generation==serviceGeneration)peerEvent(p);}));
            next.onMessage(m->fx(()->{if(generation==serviceGeneration)messageEvent(m);}));
            next.onProgress((id,p)->fx(()->{if(generation==serviceGeneration){progress.put(id,p);chatListView.refresh();}}));
            try{
                next.start(address.ip(),udp,tcp,true);
                repository.settingSave("name",name);repository.settingSave("ip",address.ip());repository.settingSave("udp",Integer.toString(udp));repository.settingSave("tcp",Integer.toString(tcp));
                synchronized(this){if(closed){next.close();return null;}service=next;}
                return "";
            }catch(Exception ex){next.close();return ex.getMessage();}
        },error->{busy=false;updateButtons();chatListView.refresh();if(error==null)return;if(!error.isEmpty())notifyUser(tr("启动失败：","Start failed: ")+error);else{myInfoLabel.setText(address.ip()+" UDP "+udp+" / TCP "+tcp);status(tr("服务已启动","Service started"));}},error->{busy=false;updateButtons();showFailure(error);});
    }
    private void stop(){
        if(busy)return;busy=true;++serviceGeneration;updateButtons();LanService current=service;
        String selectedId=selected==null?null:selected.id();
        // Stop must never wait behind hashing, outgoing sends, or a full worker queue.
        var thread=new Thread(()->{
            try{if(current!=null)current.close();fx(()->{busy=false;progress.clear();userListView.getItems().replaceAll(p->new Peer(p.id(),p.name(),p.ip(),p.port(),false));restorePeerSelection(selectedId);updateButtons();updatePeerCount();loadHistory(false);chatListView.refresh();status(tr("服务已停止","Service stopped"));});}
            catch(Exception ex){fx(()->{busy=false;updateButtons();showFailure(ex);});}
        },"lan-stop");thread.setDaemon(true);thread.start();
    }
    private void peerEvent(Peer p){
        String selectedId=selected==null?null:selected.id();
        int index=-1;for(int i=0;i<userListView.getItems().size();i++)if(userListView.getItems().get(i).id().equals(p.id())){index=i;break;}
        if(index<0)userListView.getItems().add(p);else userListView.getItems().set(index,p);
        if(selected!=null&&selected.id().equals(p.id()))selected=p;restorePeerSelection(selectedId);updatePeerCount();updateButtons();
    }
    private void restorePeerSelection(String peerId){
        if(peerId==null)return;
        for(int i=0;i<userListView.getItems().size();i++)if(peerId.equals(userListView.getItems().get(i).id())){
            var current=userListView.getSelectionModel().getSelectedItem();
            if(current==null||!peerId.equals(current.id()))userListView.getSelectionModel().select(i);
            return;
        }
    }
    private void updatePeerCount(){userCountLabel.setText(tr("联系人","Peers")+" "+userListView.getItems().size()+" · "+tr("在线","Online")+" "+userListView.getItems().stream().filter(Peer::online).count());}
    private void manualConnect(){
        var ip=new TextField();ip.setPromptText("192.168.1.10");var port=new TextField("2426");
        var content=new VBox(8,label("对方 IP / 主机名","Peer IP / host"),ip,label("对方 TCP 端口","Peer TCP port"),port);
        if(!confirm(tr("手动连接","Connect peer"),content))return;
        int target;try{target=Integer.parseInt(port.getText().trim());}catch(Exception e){notifyUser(tr("端口无效","Invalid port"));return;}
        String host=ip.getText().trim();LanService current=service;long generation=serviceGeneration;
        submit(controls,()->current.connectPeer(host,target),p->{if(generation==serviceGeneration){peerEvent(p);userListView.getSelectionModel().select(p);status(tr("连接成功","Connected"));}},this::showFailure);
    }
    private void send(){
        if(sendButton.isDisabled()||messageInput.getText().isBlank())return;
        String text=messageInput.getText();Peer peer=selected;LanService current=service;
        if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>Wire.TEXT_LIMIT){notifyUser(tr("文本超过 1 MiB，请改用文件发送。","Text exceeds 1 MiB; send it as a file."));return;}
        sending=true;updateButtons();
        submit(tasks,()->current.sendText(peer,text,persisted->fx(()->{
            if(selected!=null&&selected.id().equals(peer.id())&&messageInput.getText().equals(text))messageInput.clear();
        })),m->{sending=false;updateButtons();status(tr("对方已接收并保存","Peer received and saved"));},error->{sending=false;updateButtons();showFailure(error);});
    }
    private void offerFiles(List<File> files){
        if(selected==null||service==null||!service.running()||!service.paired(selected.id()))return;
        if(files.size()>64){notifyUser(tr("一次最多选择 64 个文件","Select at most 64 files per batch"));return;}
        Peer peer=selected;LanService current=service;
        for(var file:files)work(()->current.offer(peer,file.toPath()),m->status(tr("等待对方接收：","Awaiting acceptance: ")+m.fileName));
    }
    private boolean nearBottom(){for(Node node:chatListView.lookupAll(".scroll-bar"))if(node instanceof ScrollBar bar&&bar.getOrientation()==Orientation.VERTICAL)return bar.getValue()>=bar.getMax()-.03;return true;}
    private void messageEvent(ChatEntry e){
        liveUpdates.put(e.id,new Update(++messageRevision,e));while(liveUpdates.size()>512)liveUpdates.remove(liveUpdates.keySet().iterator().next());
        if(!Set.of("SENDING","QUEUED","TRANSFERRING").contains(e.status))progress.remove(e.id);
        boolean fresh=seenEvents.add(e.id);if(seenEvents.size()>4000)seenEvents.remove(seenEvents.iterator().next());
        if(selected==null||!selected.id().equals(e.peerId)){if(!e.outgoing&&fresh){unread.merge(e.peerId,1,Integer::sum);userListView.refresh();}return;}
        if(!historySearch.getText().isBlank()){loadHistory(false);return;}
        boolean follow=nearBottom();int found=-1;
        for(int i=0;i<chatListView.getItems().size();i++)if(chatListView.getItems().get(i).id.equals(e.id)){found=i;break;}
        if(found<0){chatListView.getItems().add(e);if(chatListView.getItems().size()>500)chatListView.getItems().remove(0);}
        else chatListView.getItems().set(found,e);
        if(follow)Platform.runLater(()->{if(!closed)chatListView.scrollTo(chatListView.getItems().size()-1);});
    }
    private void loadHistory(boolean earlier){
        if(selected==null||repository==null)return;
        String peer=selected.id(),query=historySearch.getText();long generation=++historyGeneration,revision=messageRevision;
        long before=earlier&&!chatListView.getItems().isEmpty()?chatListView.getItems().get(0).sequence:Long.MAX_VALUE;
        historyLoading=true;updateButtons();
        submit(tasks,()->repository.history(peer,before,query,50),items->{
            if(generation!=historyGeneration)return;historyLoading=false;
            var merged=new TreeMap<Long,ChatEntry>();items.forEach(m->merged.put(m.sequence,m));
            if(earlier)chatListView.getItems().forEach(m->merged.put(m.sequence,m));
            liveUpdates.values().stream().filter(u->u.revision()>revision&&u.entry().peerId.equals(peer)).map(Update::entry).forEach(m->{
                if((m.text+"\n"+m.fileName).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))merged.put(m.sequence,m);else merged.remove(m.sequence);
            });
            while(merged.size()>500){if(earlier)merged.pollLastEntry();else merged.pollFirstEntry();}
            chatListView.getItems().setAll(merged.values());chatListView.scrollTo(earlier?items.size():chatListView.getItems().size()-1);
            items.forEach(m->seenEvents.add(m.id));updateButtons();if(earlier&&items.isEmpty())status(tr("没有更早记录","No earlier messages"));
        },error->{if(generation==historyGeneration){historyLoading=false;updateButtons();showFailure(error);}});
    }
    private void clearHistory(){
        if(selected==null||service!=null&&service.running())return;
        if(!confirm(tr("清空当前会话？仅删除记录，不删除发送或接收的文件。","Clear conversation? Files on disk are not deleted."),new Label(selected.name())))return;
        String peer=selected.id();work(()->{repository.clear(peer);return null;},v->{chatListView.getItems().clear();loadHistory(false);});
    }

    private Dialog<ButtonType> dialog(String title,Node content){
        var d=new Dialog<ButtonType>();if(owner()!=null)d.initOwner(owner());d.initStyle(StageStyle.UNDECORATED);d.setTitle(title);
        var pane=d.getDialogPane();pane.getStyleClass().add("lan-messenger");
        if(rootPane.getScene()!=null){pane.getStylesheets().addAll(rootPane.getScene().getStylesheets());pane.getStylesheets().addAll(rootPane.getScene().getRoot().getStylesheets());pane.setStyle(rootPane.getScene().getRoot().getStyle());}
        pane.getStylesheets().addAll(rootPane.getStylesheets());
        d.setOnShown(e->ThemeManager.getInstance().registerScene(pane.getScene()));
        d.setOnHidden(e->ThemeManager.getInstance().unregisterScene(pane.getScene()));
        var titleLabel=new Label(title);titleLabel.setWrapText(true);titleLabel.getStyleClass().add("title");
        pane.setContent(new VBox(12,titleLabel,content));pane.setPrefWidth(560);pane.getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        ((Button)pane.lookupButton(ButtonType.OK)).getStyleClass().add("primary");
        return d;
    }
    private boolean confirm(String title,Node content){return dialog(title,content).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private void showPairing(SecureTransport.PairingRequest request,long generation){
        if(closed||generation!=serviceGeneration||request.decision().isDone()){request.decision().complete(false);return;}
        var code=new TextArea(request.verificationCode());code.setEditable(false);code.setWrapText(true);code.setPrefRowCount(3);
        var fingerprint=new TextField(request.fingerprint());fingerprint.setEditable(false);
        String display=request.name().replaceAll("[\\p{Cntrl}\\u202a-\\u202e\\u2066-\\u2069]"," ");
        var explanation=label("请通过当面或可信渠道核对两端完整校验值，完全一致后双方分别确认。设备名称并非身份凭据。","Compare the full code on both devices through a trusted channel. Both must confirm a match. A device name is not proof of identity.");
        explanation.setWrapText(true);
        var d=dialog(tr("核对并信任设备","Verify and trust device"),new VBox(10,new Label(display+"\n"+request.address()),explanation,code,label("对端公钥指纹","Peer public-key fingerprint"),fingerprint));
        var accept=(Button)d.getDialogPane().lookupButton(ButtonType.OK);caption(accept,"一致并信任","Match and trust");accept.setDefaultButton(false);
        var reject=(Button)d.getDialogPane().lookupButton(ButtonType.CANCEL);caption(reject,"拒绝","Reject");reject.setDefaultButton(true);
        d.setOnHidden(e->{ThemeManager.getInstance().unregisterScene(d.getDialogPane().getScene());request.decision().complete(d.getResult()==ButtonType.OK);});
        request.decision().whenComplete((accepted,error)->Platform.runLater(()->{if(d.isShowing())d.close();
            if(!closed&&generation==serviceGeneration&&Boolean.TRUE.equals(accepted))status(tr("已确认，等待对方确认…","Confirmed; waiting for the other device…"));}));
        d.show();
    }
    private void notifyUser(String text){var d=dialog(tr("提示","Notice"),new Label(text));d.getDialogPane().getButtonTypes().setAll(ButtonType.OK);d.showAndWait();}
    private void pasteImage(){
        if(pasteButton.isDisabled())return;
        Image image=Clipboard.getSystemClipboard().getImage();
        if(image==null){notifyUser(tr("剪贴板中没有图片","No image on clipboard"));return;}
        if(image.getWidth()*image.getHeight()>MediaSupport.MAX_PIXELS){notifyUser(tr("图片超过 2500 万像素","Image exceeds 25 megapixels"));return;}
        var preview=new ImageView(image);preview.setFitWidth(480);preview.setFitHeight(320);preview.setPreserveRatio(true);
        Peer peer=selected;LanService current=service;
        if(!confirm(tr("发送剪贴板图片给 ","Send clipboard image to ")+peer.name(),preview))return;
        long generation=serviceGeneration;
        work(()->sendPastedImage(peer,current,generation,()->{
            var buffered=SwingFXUtils.fromFXImage(image,null);byte[] png;
            try{png=MediaSupport.encodePng(buffered);}finally{buffered.flush();}
            return png;
        }),m->status(tr("图片已提交，等待接收","Image offered; awaiting acceptance")));
    }
    private ChatEntry sendPastedImage(Peer peer,LanService current,long generation,Callable<byte[]> encoder) throws Exception {
        byte[] png=encoder.call();String reference;
        try{synchronized(cachePublication){
            if(closed||generation!=serviceGeneration||service!=current||!current.running())throw new IOException("Pasted image cancelled because the service stopped");
            reference=imageCache.put(png);
        }}finally{Arrays.fill(png,(byte)0);}
        return current.offerCached(peer,reference);
    }
    private void clearClipboardCache(){
        if(service!=null&&service.running()||busy){notifyUser(tr("请先停止服务，再清理截图缓存。","Stop the service before clearing pasted images."));return;}
        if(!confirm(tr("清理截图缓存？","Clear pasted-image cache?"),label("仅删除本插件生成的粘贴截图。相关历史图片将不可预览，不删除原文件或接收文件。","Only generated pasted images are deleted. Their history previews become unavailable. Original/received files are not deleted.")))return;
        if(imageCache==null){showFailure(new IOException("Encrypted storage is unavailable"));return;}
        busy=true;++cacheGeneration;thumbnailPending.clear();updateButtons();
        submit(controls,()->{synchronized(cachePublication){return imageCache.clear();}},count->{
            busy=false;++cacheGeneration;thumbnailPending.clear();thumbnails.clear();thumbnailFailed.clear();chatListView.refresh();updateButtons();status(tr("已删除截图缓存：","Deleted pasted images: ")+count);
        },error->{busy=false;updateButtons();showFailure(error);});
    }
    private void accept(ChatEntry e){
        var chooser=new FileChooser();chooser.setInitialFileName(MediaSupport.safeName(e.fileName));File target=chooser.showSaveDialog(owner());if(target==null)return;
        boolean replace=Files.exists(target.toPath());
        if(replace&&!confirm(tr("覆盖目标文件？","Replace destination file?"),new Label(target.toString())))return;
        LanService current=service;
        work(()->{current.accept(e,target.toPath(),replace).whenComplete((v,error)->{if(error!=null)fx(()->showFailure(error));});return null;},v->{});
    }
    private java.awt.image.BufferedImage readImage(String reference,int bound) throws IOException {
        if(!EncryptedImageCache.isReference(reference))return MediaSupport.read(Path.of(reference),bound);
        byte[] bytes=imageCache.read(reference);try{return MediaSupport.read(bytes,bound);}finally{Arrays.fill(bytes,(byte)0);}
    }
    private void preview(String reference){
        String fileName=EncryptedImageCache.isReference(reference)?"clipboard.png":Path.of(reference).getFileName().toString();
        work(()->SwingFXUtils.toFXImage(readImage(reference,2048),null),image->{
            showImageViewer(reference,fileName,image);
        });
    }
    private void showImageViewer(String reference,String fileName,Image image){
        var stage=new Stage(StageStyle.DECORATED);if(owner()!=null)stage.initOwner(owner());stage.setTitle(fileName);
        var zoom=new ImageZoomModel();
        var view=new ImageView(image);view.setPreserveRatio(true);view.setSmooth(true);
        var canvas=new StackPane(view);canvas.setMinSize(0,0);canvas.getStyleClass().add("image-canvas");
        var scroll=new ScrollPane(canvas);scroll.setPannable(true);scroll.setFitToWidth(false);scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        double screenWidth=Screen.getPrimary().getVisualBounds().getWidth(),screenHeight=Screen.getPrimary().getVisualBounds().getHeight();
        double fitScale=Math.min(1,Math.min((screenWidth*.86)/Math.max(1,image.getWidth()),(screenHeight*.78)/Math.max(1,image.getHeight())));
        zoom.setScale(fitScale);
        Runnable applyZoom=()->{view.setFitWidth(image.getWidth()*zoom.scale());view.setFitHeight(image.getHeight()*zoom.scale());};
        applyZoom.run();
        var zoomLabel=new Label(Math.round(zoom.scale()*100)+"%");
        var reset=new Button(tr("适应窗口","Fit"));reset.setOnAction(e->{zoom.setScale(fitScale);applyZoom.run();zoomLabel.setText(Math.round(zoom.scale()*100)+"%");});
        var minus=new Button("−");minus.setOnAction(e->{zoom.setScale(zoom.scale()-.1);applyZoom.run();zoomLabel.setText(Math.round(zoom.scale()*100)+"%");});
        var plus=new Button("+");plus.setOnAction(e->{zoom.setScale(zoom.scale()+.1);applyZoom.run();zoomLabel.setText(Math.round(zoom.scale()*100)+"%");});
        var save=new Button(tr("另存为","Save as"));save.setOnAction(e->saveImage(reference,fileName));
        var close=new Button(tr("关闭","Close"));close.setOnAction(e->stage.close());
        var toolbar=new HBox(8,reset,minus,zoomLabel,plus,save,new Region(),close);HBox.setHgrow(toolbar.getChildren().get(5),Priority.ALWAYS);toolbar.getStyleClass().add("image-toolbar");
        scroll.addEventFilter(ScrollEvent.SCROLL,e->{if(e.getDeltaY()==0)return;e.consume();zoom.wheel(e.getDeltaY());applyZoom.run();zoomLabel.setText(Math.round(zoom.scale()*100)+"%" );});
        final double[] drag={0,0};view.setOnMousePressed(e->{drag[0]=e.getSceneX()-view.getTranslateX();drag[1]=e.getSceneY()-view.getTranslateY();});
        view.setOnMouseDragged(e->{view.setTranslateX(e.getSceneX()-drag[0]);view.setTranslateY(e.getSceneY()-drag[1]);});
        var root=new BorderPane(scroll);root.setTop(toolbar);root.getStyleClass().addAll("lan-messenger","image-viewer");
        if(rootPane.getScene()!=null)root.getStylesheets().addAll(rootPane.getScene().getStylesheets());
        var scene=new Scene(root,Math.max(900,screenWidth*.9),Math.max(650,screenHeight*.9));
        stage.setScene(scene);ThemeManager.getInstance().registerScene(scene);stage.setOnHidden(e->ThemeManager.getInstance().unregisterScene(scene));stage.show();stage.centerOnScreen();
    }
    private void saveImage(String reference,String fileName){
        var chooser=new FileChooser();chooser.setInitialFileName(MediaSupport.safeName(fileName));var target=chooser.showSaveDialog(owner());if(target==null)return;
        boolean replace=Files.exists(target.toPath());if(replace&&!confirm(tr("覆盖目标文件？","Replace destination?"),new Label(target.toString())))return;
        work(()->{if(EncryptedImageCache.isReference(reference)){byte[] bytes=imageCache.read(reference);try{Files.write(target.toPath(),bytes,replace?new StandardOpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}:new StandardOpenOption[]{StandardOpenOption.CREATE_NEW});}finally{Arrays.fill(bytes,(byte)0);}}else{Path path=Path.of(reference);if(path.toAbsolutePath().equals(target.toPath().toAbsolutePath()))return null;if(replace)Files.copy(path,target.toPath(),StandardCopyOption.REPLACE_EXISTING);else Files.copy(path,target.toPath());}return null;},v->status(tr("已保存","Saved")));
    }
    private void thumbnail(ChatEntry e,ImageView view){
        String key=e.path;Image cached=thumbnails.get(key);if(cached!=null){view.setImage(cached);return;}
        if(thumbnailPending.contains(key)||thumbnailFailed.contains(key))return;thumbnailPending.add(key);long generation=cacheGeneration;
        submit(tasks,()->{try{return SwingFXUtils.toFXImage(readImage(key,320),null);}catch(IOException ex){return null;}},image->{
            if(generation!=cacheGeneration)return;
            thumbnailPending.remove(key);if(image==null){if(thumbnailFailed.size()>256)thumbnailFailed.clear();thumbnailFailed.add(key);chatListView.refresh();return;}
            thumbnails.put(key,image);while(thumbnails.size()>64)thumbnails.remove(thumbnails.keySet().iterator().next());chatListView.refresh();
        },error->{if(generation!=cacheGeneration)return;thumbnailPending.remove(key);thumbnailFailed.add(key);chatListView.refresh();showFailure(error);});
    }
    private static String bytes(long n){return n<1024?n+" B":n<1024*1024?String.format(Locale.ROOT,"%.1f KiB",n/1024d):String.format(Locale.ROOT,"%.1f MiB",n/1048576d);}
    private String stateText(String value){return switch(value){
        case "SENDING"->tr("发送中","Sending");case "DELIVERED"->tr("已送达","Delivered");case "OFFERED"->tr("等待接收","Awaiting acceptance");
        case "QUEUED"->tr("排队中","Queued");case "TRANSFERRING"->tr("传输中","Transferring");case "COMPLETED"->tr("已完成并校验","Completed / verified");
        case "CANCELLED"->tr("已取消","Cancelled");case "REJECTED"->tr("已拒绝","Rejected");case "INTERRUPTED"->tr("已中断，请重新发送","Interrupted; resend");
        default->tr("失败","Failed");};}
    private class MessageCell extends ListCell<ChatEntry>{
        private Button action(String zh,String en,Runnable run){var button=new Button(tr(zh,en));button.setOnAction(e->run.run());return button;}
        @Override protected void updateItem(ChatEntry e,boolean empty){
            super.updateItem(e,empty);setText(null);setGraphic(null);setContextMenu(null);if(empty||e==null)return;
            var status=MessageStatusPolicy.showDeliveryReceipt(e.status,e.outgoing)?stateText(e.status):(!e.outgoing&&"DELIVERED".equals(e.status)?"":stateText(e.status));
            var headerText=(e.outgoing?tr("我","Me"):e.senderName)+" · "+clock.format(Instant.ofEpochMilli(e.time));
            var header=new Label(status.isBlank()?headerText:headerText+" · "+status);header.getStyleClass().add("muted");
            var box=new VBox(6,header);box.getStyleClass().addAll("message-card",e.outgoing?"outgoing":"incoming");box.setMaxWidth(Double.MAX_VALUE);box.maxWidthProperty().bind(chatListView.widthProperty().multiply(.72));
            var menu=new ContextMenu();var copy=new MenuItem(tr("复制","Copy"));copy.setOnAction(event->{var c=new ClipboardContent();c.putString(e.attachment()?e.fileName:e.text);Clipboard.getSystemClipboard().setContent(c);});menu.getItems().add(copy);setContextMenu(menu);
            if(!e.attachment()){var text=new Label(e.text);text.setWrapText(true);text.setMaxWidth(Double.MAX_VALUE);box.getChildren().add(text);}
            else{
                box.getChildren().add(new Label(e.fileName+" · "+bytes(e.size)));
                if(e.kind.equals("IMAGE")&&!e.path.isEmpty()&&(e.outgoing||e.status.equals("COMPLETED"))){
                    if(thumbnailFailed.contains(e.path))box.getChildren().add(label("图片缺失、损坏或超过预览限制","Image missing, corrupt or exceeds preview limit"));
                    var image=new ImageView();image.setFitWidth(240);image.setFitHeight(160);image.setPreserveRatio(true);image.setOnMouseClicked(event->{if(event.getClickCount()>=2)preview(e.path);});thumbnail(e,image);box.getChildren().add(image);
                    box.getChildren().add(action("查看图片","Preview",()->preview(e.path)));
                }
                var p=progress.get(e.id);if(p!=null){var bar=new ProgressBar(p.total()==0?1:(double)p.bytes()/p.total());bar.setMaxWidth(Double.MAX_VALUE);box.getChildren().addAll(bar,new Label(bytes(p.bytes())+" / "+bytes(p.total())+" · "+bytes(p.bytes()*1000/Math.max(1,p.elapsedMillis()))+"/s"));}
                var actions=new HBox(8);box.getChildren().add(actions);
                boolean running=service!=null&&service.running();actions.setDisable(!running);
                if(!e.outgoing&&Set.of("OFFERED","FAILED").contains(e.status)){actions.getChildren().add(action("接收 / 重试","Accept / retry",()->accept(e)));actions.getChildren().add(action("拒绝","Reject",()->{LanService current=service;submit(controls,()->{current.reject(e);return null;},v->{},LanMessengerController.this::showFailure);}));}
                if(Set.of("OFFERED","QUEUED","TRANSFERRING","SENDING").contains(e.status))actions.getChildren().add(action("取消传输","Cancel",()->{LanService current=service;submit(controls,()->{current.cancel(e.id);return null;},v->{},LanMessengerController.this::showFailure);}));
                if(!e.outgoing&&Set.of("FAILED","INTERRUPTED").contains(e.status))box.getChildren().add(label("若传输已开始或服务重启，请发送方重新发送。","If transfer already started or service restarted, ask sender to resend."));
                if(e.status.equals("COMPLETED")&&!e.path.isEmpty()){var path=new Label(e.path);path.setWrapText(true);box.getChildren().add(path);}
            }
            if(!e.error.isEmpty()){var error=new Label(e.error);error.setWrapText(true);box.getChildren().add(error);}
            if(e.outgoing&&Set.of("FAILED","INTERRUPTED","CANCELLED","REJECTED").contains(e.status)){var retry=action("重新发送","Retry",()->{LanService current=service;work(()->current.retry(e),v->{});});retry.setDisable(service==null||!service.running());box.getChildren().add(retry);}
            var row=new HBox(box);row.setMaxWidth(Double.MAX_VALUE);row.setAlignment(e.outgoing?Pos.CENTER_RIGHT:Pos.CENTER_LEFT);row.getStyleClass().addAll("message-row",e.outgoing?"outgoing":"incoming");row.prefWidthProperty().bind(chatListView.widthProperty().subtract(20));setGraphic(row);
        }
    }
    public synchronized void dispose(){
        if(closed)return;closed=true;ready.completeExceptionally(new CancellationException("Plugin closed"));tasks.shutdownNow();controls.shutdownNow();
        var current=service;var currentVault=vault;
        var closer=new Thread(()->{if(current!=null)current.close();if(currentVault!=null)try{currentVault.close();}catch(IOException ignored){}},"lan-close");closer.setDaemon(true);closer.start();
        if(Platform.isFxApplicationThread())searchDelay.stop();else Platform.runLater(searchDelay::stop);
    }
}
