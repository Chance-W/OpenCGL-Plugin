package com.opencgl.lanmsg.ui;

import com.opencgl.lanmsg.controller.LanMessengerController;
import com.opencgl.lanmsg.i18n.I18N;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextArea;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import com.opencgl.lanmsg.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LanViewTest {
    @TempDir Path dir;
    private final com.opencgl.lanmsg.security.MemorySecretStore keys=new com.opencgl.lanmsg.security.MemorySecretStore();
    private void seed(java.util.function.Consumer<ChatRepository> action) throws Exception {
        try(var vault=com.opencgl.lanmsg.security.LocalVault.open(dir.resolve("security"),keys)) {
            action.accept(new ChatRepository(dir.resolve("secure-history.db"),vault));
        }
    }
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CountDownLatch(1);
        try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}
        catch(IllegalStateException e){ready.countDown();}
        assertTrue(ready.await(15,TimeUnit.SECONDS));
    }
    @Test void loadsRealFxmlWithMultilineComposerAndHistoryActions() throws Exception {
        var loaded=new CompletableFuture<LanMessengerController>();
        Platform.runLater(()->{
            try{
                var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
                loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();
                loaded.complete(loader.getController());
            }catch(Throwable e){loaded.completeExceptionally(e);}
        });
        var controller=loaded.get(15,TimeUnit.SECONDS);
        try{
            controller.ready().get(15,TimeUnit.SECONDS);
            var checked=new CompletableFuture<Void>();
            Platform.runLater(()->{
                try{
                    assertInstanceOf(TextArea.class,controller.messageInput);
                    assertNotNull(controller.historySearch);
                    assertNotNull(controller.earlierButton.getOnAction());
                    assertNotNull(controller.manualButton.getOnAction());
                    assertTrue(controller.sendButton.isDisabled());
                    assertFalse(controller.startServiceButton.isDisabled());
                    checked.complete(null);
                }catch(Throwable e){checked.completeExceptionally(e);}
            });
            checked.get(15,TimeUnit.SECONDS);
        }finally{controller.dispose();}
    }
    @Test void selectingPeerLoadsLatestHistoryAndEarlierButtonPrependsPreviousPage() throws Exception {
        var peer=new Peer("test-peer","Alice","127.0.0.1",2426,false);
        seed(repo->{repo.savePeer(peer);for(int i=0;i<75;i++){var entry=new ChatEntry();entry.peerId=peer.id();entry.text="message-"+i;entry.status="DELIVERED";repo.save(entry);}});
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
            loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();return (LanMessengerController)loader.getController();
        });
        try {
            controller.ready().get(10,TimeUnit.SECONDS);
            onFx(()->{controller.userListView.getSelectionModel().select(0);return null;});
            await(()->controller.chatListView.getItems().size()==50);
            assertEquals("message-25",onFx(()->controller.chatListView.getItems().get(0).text));
            onFx(()->{controller.earlierButton.fire();return null;});
            await(()->controller.chatListView.getItems().size()==75);
            assertEquals("message-0",onFx(()->controller.chatListView.getItems().get(0).text));
            onFx(()->{controller.historySearch.setText("message-74");return null;});
            await(()->controller.chatListView.getItems().size()==1);
            assertEquals("message-74",onFx(()->controller.chatListView.getItems().get(0).text));
        }finally{controller.dispose();}
    }
    @Test void composerTextFollowsDarkAndLightThemeTokens() throws Exception {
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
            loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();return (LanMessengerController)loader.getController();
        });
        try {
            controller.ready().get(10,TimeUnit.SECONDS);
            onFx(()->{
                var scene=new Scene(controller.rootPane,1000,750);
                controller.rootPane.setStyle("-theme-bg-primary: #202020; -theme-bg-secondary: #282828; -theme-bg-tertiary: #333333; -theme-text-primary: #eeeeee; -theme-text-secondary: #aaaaaa; -theme-text-inverse: white; -theme-accent: #00aa99;");
                controller.rootPane.applyCss();controller.rootPane.layout();controller.messageInput.setText("test");
                assertEquals(Color.web("#eeeeee"),controller.messageInput.getCssMetaData().stream().filter(m->m.getProperty().equals("-fx-text-fill")).findFirst().map(m->((javafx.css.CssMetaData)m).getStyleableProperty(controller.messageInput).getValue()).orElseThrow());
                controller.rootPane.setStyle(controller.rootPane.getStyle().replace("#eeeeee","#222222"));controller.rootPane.applyCss();
                assertEquals(Color.web("#222222"),controller.messageInput.getCssMetaData().stream().filter(m->m.getProperty().equals("-fx-text-fill")).findFirst().map(m->((javafx.css.CssMetaData)m).getStyleableProperty(controller.messageInput).getValue()).orElseThrow());
                return null;
            });
        }finally{controller.dispose();}
    }
    private static <T> T onFx(Callable<T> action) throws Exception {
        var future=new CompletableFuture<T>();Platform.runLater(()->{try{future.complete(action.call());}catch(Throwable ex){future.completeExceptionally(ex);}});return future.get(10,TimeUnit.SECONDS);
    }
    @Test void unavailableKeyStoreBlocksInitializationWithoutCreatingPlaintextHistory() throws Exception {
        com.opencgl.lanmsg.security.SecretStore locked=new com.opencgl.lanmsg.security.SecretStore() {
            public java.util.Optional<byte[]> read() throws java.io.IOException {
                assertFalse(Platform.isFxApplicationThread());throw new java.io.IOException("Key store locked");
            }
            public void create(byte[] value){fail("Must not create a key after a credential error");}
        };
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
            loader.setControllerFactory(type->new LanMessengerController(dir,locked));loader.load();return (LanMessengerController)loader.getController();
        });
        try {
            assertThrows(ExecutionException.class,()->controller.ready().get(10,TimeUnit.SECONDS));
            assertTrue(onFx(controller.startServiceButton::isDisabled));
            assertFalse(java.nio.file.Files.exists(dir.resolve("history.db")));
            assertFalse(java.nio.file.Files.exists(dir.resolve("secure-history.db")));
        }finally{controller.dispose();}
    }
    @Test void existingEncryptedHistoryWithMissingVaultMarkerCannotGenerateReplacementKey() throws Exception {
        seed(repo->repo.settingSave("name","保留配置"));
        java.nio.file.Files.delete(dir.resolve("security/vault.check"));
        var creates=new java.util.concurrent.atomic.AtomicInteger();
        com.opencgl.lanmsg.security.SecretStore empty=new com.opencgl.lanmsg.security.SecretStore(){
            public java.util.Optional<byte[]> read(){return java.util.Optional.empty();}
            public void create(byte[] value){creates.incrementAndGet();}
        };
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
            loader.setControllerFactory(type->new LanMessengerController(dir,empty));loader.load();return (LanMessengerController)loader.getController();
        });
        try{assertThrows(ExecutionException.class,()->controller.ready().get(10,TimeUnit.SECONDS));assertEquals(0,creates.get());}
        finally{controller.dispose();}
    }
    @Test void liveFirstMessageSurvivesEmptyHistorySnapshot() throws Exception {
        var peer=new Peer("live-peer","Alice","127.0.0.1",2426,false);seed(repo->repo.savePeer(peer));
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();return (LanMessengerController)loader.getController();
        });
        try {
            controller.ready().get(10,TimeUnit.SECONDS);
            onFx(()->{
                controller.userListView.getSelectionModel().select(0);
                // Deliver a transport event while the FX application of the older empty DB snapshot is pending.
                var message=new ChatEntry();message.peerId=peer.id();message.senderName="Alice";message.sequence=1;message.text="first incoming";message.status="DELIVERED";
                var event=LanMessengerController.class.getDeclaredMethod("messageEvent",ChatEntry.class);event.setAccessible(true);event.invoke(controller,message);return null;
            });
            await(()->!controller.earlierButton.isDisabled());
            assertEquals("first incoming",onFx(()->controller.chatListView.getItems().get(0).text));
        }finally{controller.dispose();}
    }
    @Test void fullWorkQueuePreservesDraftAndCannotBlockStop() throws Exception {
        var remoteRepo=new ChatRepository(dir.resolve("remote.db"));var peer=new Peer(remoteRepo.identity(),"Alice","127.0.0.1",2426,false);seed(repo->repo.savePeer(peer));
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();return (LanMessengerController)loader.getController();
        });
        var release=new CountDownLatch(1);
        controller.ready().get(10,TimeUnit.SECONDS);
        var repoField=LanMessengerController.class.getDeclaredField("repository");repoField.setAccessible(true);var repo=(ChatRepository)repoField.get(controller);
        try(var running=com.opencgl.lanmsg.security.SecureFixtures.service(repo,dir.resolve("vault"),"local");var remote=com.opencgl.lanmsg.security.SecureFixtures.service(remoteRepo,dir.resolve("remote-vault"),"Alice")) {
            controller.ready().get(10,TimeUnit.SECONDS);running.start("127.0.0.1",0,0,false);
            remote.start("127.0.0.1",0,0,false);running.connectPeer("127.0.0.1",remote.port());
            onFx(()->{controller.userListView.getSelectionModel().select(0);return null;});await(()->!controller.earlierButton.isDisabled());
            var field=LanMessengerController.class.getDeclaredField("tasks");field.setAccessible(true);var executor=(ThreadPoolExecutor)field.get(controller);
            var occupied=new CountDownLatch(3);
            for(int i=0;i<3;i++)executor.execute(()->{occupied.countDown();try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
            assertTrue(occupied.await(5,TimeUnit.SECONDS));while(executor.getQueue().offer(()->{})){}
            onFx(()->{
                var serviceField=LanMessengerController.class.getDeclaredField("service");serviceField.setAccessible(true);serviceField.set(controller,running);
                var update=LanMessengerController.class.getDeclaredMethod("updateButtons");update.setAccessible(true);update.invoke(controller);
                assertFalse(controller.sendButton.isDisabled());controller.messageInput.setText("preserve draft");controller.sendButton.fire();
                assertEquals("preserve draft",controller.messageInput.getText());
                controller.stopServiceButton.fire();return null;
            });
            await(()->!running.running());await(()->!controller.startServiceButton.isDisabled());
            assertEquals("preserve draft",onFx(controller.messageInput::getText));
        }finally{release.countDown();controller.dispose();com.opencgl.lanmsg.security.SecureFixtures.closeAll();}
    }
    @Test void stoppedPasteCannotRecreateImageAfterCacheClear() throws Exception {
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
            loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();return (LanMessengerController)loader.getController();
        });
        controller.ready().get(10,TimeUnit.SECONDS);
        var repoField=LanMessengerController.class.getDeclaredField("repository");repoField.setAccessible(true);var repo=(ChatRepository)repoField.get(controller);
        var cacheField=LanMessengerController.class.getDeclaredField("imageCache");cacheField.setAccessible(true);var cache=(com.opencgl.lanmsg.security.EncryptedImageCache)cacheField.get(controller);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var running=com.opencgl.lanmsg.security.SecureFixtures.service(repo,dir.resolve("transport"),"local");var worker=Executors.newVirtualThreadPerTaskExecutor()) {
            running.start("127.0.0.1",0,0,false);
            onFx(()->{
                var field=LanMessengerController.class.getDeclaredField("service");field.setAccessible(true);field.set(controller,running);
                var update=LanMessengerController.class.getDeclaredMethod("updateButtons");update.setAccessible(true);update.invoke(controller);return null;
            });
            var send=LanMessengerController.class.getDeclaredMethod("sendPastedImage",Peer.class,LanService.class,long.class,Callable.class);send.setAccessible(true);
            var pending=worker.submit(()->send.invoke(controller,new Peer("peer","peer","127.0.0.1",2426,false),running,0L,(Callable<byte[]>)()->{
                entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("encoder latch timeout");return new byte[]{1,2,3};
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            onFx(()->{controller.stopServiceButton.fire();return null;});await(()->!running.running());await(()->!controller.startServiceButton.isDisabled());
            assertEquals(0,cache.clear());release.countDown();assertThrows(ExecutionException.class,()->pending.get(5,TimeUnit.SECONDS));
            assertEquals(0,cache.clear(),"A stopped encoder must not publish another screenshot after clearing");
        }finally{release.countDown();controller.dispose();com.opencgl.lanmsg.security.SecureFixtures.closeAll();}
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!onFx(condition)){if(System.nanoTime()>deadline)fail("UI condition timed out");Thread.sleep(20);}
    }
    @Test void pairingDialogShowsFullCodeDefaultsToRejectAndClosesOnCancellation() throws Exception {
        var controller=onFx(()->{
            var loader=new FXMLLoader(getClass().getResource("/com/opencgl/lanmsg/views/LanMessengerView.fxml"),I18N.getBundle(I18N.getLocale()));
            loader.setControllerFactory(type->new LanMessengerController(dir,keys));loader.load();return (LanMessengerController)loader.getController();
        });
        var decision=new CompletableFuture<Boolean>();var second=new CompletableFuture<Boolean>();
        String code="01234567 89ABCDEF 01234567 89ABCDEF 01234567 89ABCDEF 01234567 89ABCDEF";
        try{
            controller.ready().get(10,TimeUnit.SECONDS);
            var show=LanMessengerController.class.getDeclaredMethod("showPairing",com.opencgl.lanmsg.security.SecureTransport.PairingRequest.class,long.class);show.setAccessible(true);
            var pane=onFx(()->{
                new Scene(controller.rootPane,1000,750);
                controller.rootPane.setStyle("-theme-bg-primary: #202020; -theme-bg-secondary: #282828; -theme-bg-tertiary: #333333; -theme-text-primary: #eeeeee; -theme-text-secondary: #aaaaaa; -theme-text-inverse: white; -theme-accent: #00aa99;");
                show.invoke(controller,new com.opencgl.lanmsg.security.SecureTransport.PairingRequest("peer","中文设备📎","127.0.0.1","ab".repeat(32),code,decision),0L);
                return (javafx.scene.control.DialogPane)javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null)
                        .map(w->w.getScene().lookup(".dialog-pane")).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            });
            onFx(()->{
                assertFalse(((javafx.scene.control.Button)pane.lookupButton(javafx.scene.control.ButtonType.OK)).isDefaultButton());
                assertTrue(((javafx.scene.control.Button)pane.lookupButton(javafx.scene.control.ButtonType.CANCEL)).isDefaultButton());
                assertEquals(code,((TextArea)pane.lookup(".text-area")).getText());return null;
            });
            var window=onFx(()->pane.getScene().getWindow());
            assertFalse(decision.isDone());decision.complete(false);
            await(()->!window.isShowing());
            onFx(()->{
                show.invoke(controller,new com.opencgl.lanmsg.security.SecureTransport.PairingRequest("peer","中文设备📎","127.0.0.1","ab".repeat(32),code,second),0L);
                var active=(javafx.scene.control.DialogPane)javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null)
                        .map(w->w.getScene().lookup(".dialog-pane")).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
                ((javafx.scene.control.Button)active.lookupButton(javafx.scene.control.ButtonType.OK)).fire();return null;
            });
            assertTrue(second.get(1,TimeUnit.SECONDS));
        }finally{decision.complete(false);second.complete(false);controller.dispose();}
    }
}
