package com.opencgl.lanmsg.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.opencgl.lanmsg.security.SecureTransport;
import com.opencgl.lanmsg.security.EncryptedImageCache;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** TLS-gated transport. Public blocking operations must be invoked outside the JavaFX thread. */
public final class LanService implements AutoCloseable {
    public static final long MAX_FILE_SIZE=1024L*1024*1024;
    private final ChatRepository repository;
    private final String id;
    private final SecureTransport secure;
    private final long socketDeadlineMillis;
    private volatile String name;
    private volatile Session session;
    private Consumer<Peer> peerListener=p->{};
    private Consumer<ChatEntry> messageListener=e->{};
    private BiConsumer<String,Progress> progressListener=(id,p)->{};
    public record Progress(long bytes,long total,long elapsedMillis) {}
    private static final class Offer {
        final ChatEntry entry; final AtomicBoolean claimed=new AtomicBoolean();
        Offer(ChatEntry e){entry=e.copy();}
    }
    private static final class Session {
        final String ip; final ServerSocket tcp; final DatagramSocket udp;
        final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
        final Map<String,Socket> transfers=new ConcurrentHashMap<>();
        final Map<Socket,ScheduledFuture<?>> deadlines=new ConcurrentHashMap<>();
        final Map<String,CompletableFuture<Void>> pending=new ConcurrentHashMap<>();
        final Map<String,Offer> offers=new ConcurrentHashMap<>();
        final Set<String> cancelled=ConcurrentHashMap.newKeySet();
        final Map<String,Long> seen=new ConcurrentHashMap<>();
        final Map<String,Peer> peers=new ConcurrentHashMap<>();
        final Set<String> authenticated=ConcurrentHashMap.newKeySet();
        final ThreadPoolExecutor workers=pool("lan-inbound",6,48);
        final ThreadPoolExecutor downloads=pool("lan-download",2,64);
        final Semaphore transferSlots=new Semaphore(2);
        final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,daemon("lan-heartbeat"));
        volatile boolean open=true;
        Session(String ip,ServerSocket tcp,DatagramSocket udp){this.ip=ip;this.tcp=tcp;this.udp=udp;timer.setRemoveOnCancelPolicy(true);}
    }
    private static ThreadFactory daemon(String name){return r->{var t=new Thread(r,name);t.setDaemon(true);return t;};}
    private static ThreadPoolExecutor pool(String name,int threads,int queue){return new ThreadPoolExecutor(threads,threads,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queue),daemon(name),new ThreadPoolExecutor.AbortPolicy());}
    private final EncryptedImageCache imageCache;
    public LanService(ChatRepository repository,String name,SecureTransport secure){this(repository,name,secure,java.time.Duration.ofMinutes(10),null);}
    public LanService(ChatRepository repository,String name,SecureTransport secure,EncryptedImageCache imageCache){this(repository,name,secure,java.time.Duration.ofMinutes(10),imageCache);}
    /** Absolute socket deadline also bounds writes, unlike SO_TIMEOUT (which only bounds reads). */
    public LanService(ChatRepository repository,String name,SecureTransport secure,java.time.Duration socketDeadline){
        this(repository,name,secure,socketDeadline,null);
    }
    private LanService(ChatRepository repository,String name,SecureTransport secure,java.time.Duration socketDeadline,EncryptedImageCache imageCache){
        this.imageCache=imageCache;
        this.repository=repository;this.id=repository.identity();this.name=name;
        this.secure=Objects.requireNonNull(secure);
        if(!id.equals(secure.deviceId()))throw new IllegalArgumentException("Repository identity and TLS identity differ");
        if(socketDeadline.isNegative()||socketDeadline.isZero())throw new IllegalArgumentException("Positive socket deadline required");
        this.socketDeadlineMillis=Math.max(1,socketDeadline.toMillis());
    }
    public ChatRepository repository(){return repository;}
    public String id(){return id;}
    public int port(){var s=session;return s==null?0:s.tcp.getLocalPort();}
    public boolean running(){return session!=null;}
    public boolean paired(String peerId){var s=session;return s!=null&&s.authenticated.contains(peerId);}
    public void onPeer(Consumer<Peer> listener){peerListener=Objects.requireNonNull(listener);}
    public void onMessage(Consumer<ChatEntry> listener){messageListener=Objects.requireNonNull(listener);}
    public void onProgress(BiConsumer<String,Progress> listener){progressListener=Objects.requireNonNull(listener);}
    public synchronized void start(String ip,int udpPort,int tcpPort,boolean broadcast) throws IOException {
        if(session!=null)throw new IOException("Already running");
        if(name==null||name.isBlank()||name.length()>256)throw new IOException("Invalid device name");
        if(udpPort<0||udpPort>65535||tcpPort<0||tcpPort>65535)throw new IOException("Invalid port");
        InetAddress address=InetAddress.getByName(ip);
        if(!(address instanceof Inet4Address)||NetworkInterface.getByInetAddress(address)==null)throw new IOException("Select a local IPv4 interface");
        var tcp=new ServerSocket(); DatagramSocket udp=null;
        try {
            tcp.setReuseAddress(true);tcp.bind(new InetSocketAddress(address,tcpPort));
            udp=new DatagramSocket(null);udp.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"),udpPort));udp.setBroadcast(true);
            var s=new Session(ip,tcp,udp);session=s;
            daemon("lan-accept").newThread(()->acceptLoop(s)).start();
            daemon("lan-discovery").newThread(()->discoveryLoop(s)).start();
            if(broadcast){announce(s,"DISCOVER");s.timer.scheduleAtFixedRate(()->announce(s,"ANNOUNCE"),30,30,TimeUnit.SECONDS);}
            s.timer.scheduleAtFixedRate(()->expire(s),15,15,TimeUnit.SECONDS);
        }catch(Exception e){try{tcp.close();}catch(IOException ignored){}if(udp!=null)udp.close();session=null;throw new IOException("Start failed: "+ip+" UDP "+udpPort+" / TCP "+tcpPort+": "+e.getMessage(),e);}
    }
    private JSONObject frame(Session s,String op){var f=Wire.frame(op);f.put("device",id);f.put("name",name);f.put("port",s.tcp.getLocalPort());return f;}
    private Session require() throws IOException {var s=session;if(s==null||!s.open)throw new IOException("Service is not running");return s;}
    private void acceptLoop(Session s){
        while(s.open)try{
            Socket socket=s.tcp.accept();socket.setSoTimeout(15000);register(s,socket);
            try{s.workers.execute(()->handle(s,socket));}catch(RejectedExecutionException e){closeSocket(s,socket);}
        }catch(IOException e){if(s.open)System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,e.getMessage());}
    }
    private Peer readPeer(JSONObject f,String ip) throws IOException {
        String device=Wire.required(f,"device",64);try{UUID.fromString(device);}catch(IllegalArgumentException e){throw new IOException("Invalid device identity");}
        int port=f.getIntValue("port");if(port<1||port>65535)throw new IOException("Invalid peer port");
        if(device.equals(id))throw new IOException("Cannot connect to this device itself");
        return new Peer(device,Wire.required(f,"name",256),ip,port,true);
    }
    private void remember(Session s,Peer p){
        synchronized(this){if(session!=s)return;s.authenticated.add(p.id());Peer previous=s.peers.put(p.id(),p);s.seen.put(p.id(),System.nanoTime());
            if(!p.equals(previous)){repository.savePeer(p);notifyPeer(s,p);}}
    }
    private void handle(Session s,Socket socket){
        boolean authorized=false;
        try{
            socket=upgrade(s,socket,false,"",false);authorized=true;
            var f=Wire.read(socket.getInputStream());var peer=readPeer(f,socket.getInetAddress().getHostAddress());
            if(!peer.id().equals(secure.peerId(socket)))throw new IOException("Frame identity differs from TLS peer");
            String op=f.getString("op");
            if(!"HELLO".equals(op)&&!id.equals(f.getString("to")))throw new IOException("Wrong recipient");
            synchronized(this){secure.peerId(socket);remember(s,peer);}
            switch(op){
                case "HELLO" -> Wire.write(socket.getOutputStream(),frame(s,"HELLO_ACK"));
                case "TEXT","OFFER" -> {
                    var e=incoming(f,peer);boolean fresh;
                    synchronized(this){if(session!=s)throw new IOException("Service stopped");secure.peerId(socket);fresh=repository.insertIncoming(e);}
                    if(fresh)emit(s,e);
                    var ack=frame(s,"ACK");ack.put("id",e.id);Wire.write(socket.getOutputStream(),ack);
                }
                case "GET" -> upload(s,socket,f,peer);
                case "REJECT","CANCEL" -> {
                    String messageId=Wire.required(f,"id",64);var e=repository.find(messageId);
                    if(e==null||!e.peerId.equals(peer.id())||!e.token.equals(Wire.required(f,"token",128)))throw new IOException("Unknown transfer");
                    synchronized(this){secure.peerId(socket);cancelLocal(s,e,"REJECT".equals(op)?"REJECTED":"CANCELLED");}
                    var ack=frame(s,"ACK");ack.put("id",e.id);Wire.write(socket.getOutputStream(),ack);
                }
                default -> throw new IOException("Unknown operation");
            }
        }catch(Exception e){if(authorized)try{var error=frame(s,"ERROR");error.put("error",safeError(e));Wire.write(socket.getOutputStream(),error);}catch(Exception ignored){} }
        finally{closeSocket(s,socket);}
    }
    private ChatEntry incoming(JSONObject f,Peer peer) throws IOException {
        var e=new ChatEntry();e.id=Wire.required(f,"id",64);try{UUID.fromString(e.id);}catch(Exception ex){throw new IOException("Invalid message ID");}
        e.peerId=peer.id();e.senderId=peer.id();e.senderName=peer.name();
        if("TEXT".equals(f.getString("op"))){e.text=f.getString("text");e.status="DELIVERED";}
        else{
            e.kind=f.getString("kind");if(!Set.of("FILE","IMAGE").contains(e.kind))throw new IOException("Invalid attachment type");
            e.fileName=Wire.required(f,"fileName",255);
            if(e.fileName.contains("/")||e.fileName.contains("\\")||e.fileName.equals(".")||e.fileName.equals("..")||e.fileName.chars().anyMatch(c->c<32))throw new IOException("Invalid filename");
            e.size=f.getLongValue("size");if(e.size<0||e.size>MAX_FILE_SIZE)throw new IOException("File exceeds 1 GiB");
            e.sha256=Wire.required(f,"sha256",64);if(!e.sha256.matches("[0-9a-f]{64}"))throw new IOException("Invalid SHA-256");
            e.token=Wire.required(f,"token",128);e.expires=f.getLongValue("expires");
            if(e.expires<System.currentTimeMillis()||e.expires>System.currentTimeMillis()+TimeUnit.HOURS.toMillis(1))throw new IOException("Expired file offer");
            e.status="OFFERED";
        }return e;
    }
    private Socket connect(Session s,Peer peer) throws IOException {return connect(s,peer,false);}
    private Socket connect(Session s,Peer peer,boolean pair) throws IOException {
        var socket=new Socket();register(s,socket);
        try{socket.bind(new InetSocketAddress(s.ip,0));socket.connect(new InetSocketAddress(peer.ip(),peer.port()),5000);socket.setSoTimeout(15000);check(s,"");return upgrade(s,socket,true,peer.id(),pair);}
        catch(IOException e){closeSocket(s,socket);throw e;}
    }
    private Socket upgrade(Session s,Socket raw,boolean initiator,String expectedId,boolean pairing) throws IOException {
        Socket encrypted=secure.authorize(raw,initiator,expectedId,pairing);
        try{register(s,encrypted);s.sockets.remove(raw);var deadline=s.deadlines.remove(raw);if(deadline!=null)deadline.cancel(false);return encrypted;}
        catch(IOException failure){secure.release(encrypted);throw failure;}
    }
    private synchronized void register(Session s,Socket socket) throws IOException {
        if(!s.open||session!=s){socket.close();throw new IOException("Service stopped");}
        s.sockets.add(socket);
        s.deadlines.put(socket,s.timer.schedule(()->closeSocket(s,socket),socketDeadlineMillis,TimeUnit.MILLISECONDS));
    }
    private JSONObject exchange(Session s,Peer peer,JSONObject request) throws IOException {
        Socket socket=connect(s,peer,"HELLO".equals(request.getString("op")));
        try{Wire.write(socket.getOutputStream(),request);var response=Wire.read(socket.getInputStream());
            if("ERROR".equals(response.getString("op")))throw new IOException(response.getString("error"));
            if(!secure.peerId(socket).equals(response.getString("device")))throw new IOException("Response identity differs from TLS peer");return response;
        }finally{closeSocket(s,socket);}
    }
    public Peer connectPeer(String ip,int port) throws IOException {
        var s=require();if(port<1||port>65535)throw new IOException("Invalid TCP port");
        Socket socket=connect(s,new Peer("","",ip,port,true),true);
        try{
            Wire.write(socket.getOutputStream(),frame(s,"HELLO"));var response=Wire.read(socket.getInputStream());
            if(!"HELLO_ACK".equals(response.getString("op")))throw new IOException("Handshake rejected");
            var peer=readPeer(response,socket.getInetAddress().getHostAddress());
            synchronized(this){if(!peer.id().equals(secure.peerId(socket)))throw new IOException("Peer identity changed");remember(s,peer);secure.peerId(socket);}
            return peer;
        }finally{closeSocket(s,socket);}
    }
    private ChatEntry outgoing(Peer peer){var e=new ChatEntry();e.peerId=peer.id();e.senderId=id;e.senderName=name;e.outgoing=true;return e;}
    public ChatEntry sendText(Peer peer,String text) throws IOException {
        return sendText(peer,text,e->{});
    }
    /** Notify acceptance only after durable storage; callers can then replace a draft with its retryable card. */
    public ChatEntry sendText(Peer peer,String text,Consumer<ChatEntry> persisted) throws IOException {
        if(text==null||text.isBlank())throw new IOException("Message is empty");
        var e=outgoing(peer);e.text=text;return sendEntry(require(),peer,e,persisted);
    }
    public ChatEntry retry(ChatEntry entry) throws IOException {
        if(!entry.outgoing||!entry.senderId.equals(id))throw new IOException("Only sent messages can be retried");
        Peer peer=peer(entry.peerId);
        if(entry.attachment())return EncryptedImageCache.isReference(entry.path)?offerCached(peer,entry.path):offer(peer,Path.of(entry.path));
        return sendEntry(require(),peer,entry.copy());
    }
    private Peer peer(String peerId) throws IOException {return repository.peers().stream().filter(p->p.id().equals(peerId)).findFirst().orElseThrow(()->new IOException("Unknown peer"));}
    private ChatEntry sendEntry(Session s,Peer peer,ChatEntry e) throws IOException {
        return sendEntry(s,peer,e,message->{});
    }
    private ChatEntry sendEntry(Session s,Peer peer,ChatEntry e,Consumer<ChatEntry> persisted) throws IOException {
        e.status="SENDING";e.error="";var f=frame(s,e.attachment()?"OFFER":"TEXT");
        f.put("id",e.id);f.put("to",peer.id());
        if(e.attachment()){f.put("kind",e.kind);f.put("fileName",e.fileName);f.put("size",e.size);f.put("sha256",e.sha256);f.put("token",e.token);f.put("expires",e.expires);}
        else f.put("text",e.text);
        // Validate before persisting an unsendable message.
        Wire.write(OutputStream.nullOutputStream(),f);
        synchronized(this){check(s,e.id);state(s,e,"SENDING","");var saved=repository.find(e.id);if(saved!=null)notifySafely(()->persisted.accept(saved));}
        try{var ack=exchange(s,peer,f);if(!"ACK".equals(ack.getString("op"))||!e.id.equals(ack.getString("id")))throw new IOException("Missing delivery ACK");
            finishSend(s,e,e.attachment()?"OFFERED":"DELIVERED","");return repository.find(e.id);
        }catch(Exception failure){finishSend(s,e,"FAILED",safeError(failure));throw new IOException(safeError(failure),failure);}
    }
    private synchronized void finishSend(Session s,ChatEntry entry,String status,String error){
        var current=repository.find(entry.id);
        // GET may have already advanced the attachment while its OFFER ACK was in flight.
        if(current!=null&&"SENDING".equals(current.status)){
            state(s,current,status,error);if(status.equals("FAILED")&&entry.attachment())s.offers.remove(entry.id);
        }
    }
    public ChatEntry offer(Peer peer,Path source) throws IOException {
        var s=require();if(!Files.isRegularFile(source))throw new IOException("Select a regular file");
        long size=Files.size(source);if(size>MAX_FILE_SIZE)throw new IOException("File exceeds 1 GiB");
        var e=outgoing(peer);e.fileName=source.getFileName().toString();e.path=source.toAbsolutePath().toString();e.size=size;
        String lower=e.fileName.toLowerCase(Locale.ROOT);e.kind=lower.endsWith(".png")||lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".gif")?"IMAGE":"FILE";
        e.sha256=hash(source,()->!s.open);e.token=UUID.randomUUID().toString()+UUID.randomUUID();e.expires=System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(30);
        if(s.offers.size()>=128)throw new IOException("Too many pending file offers");
        s.offers.put(e.id,new Offer(e));return sendEntry(s,peer,e);
    }
    public ChatEntry offerCached(Peer peer,String reference) throws IOException {
        var s=require();if(imageCache==null)throw new IOException("Encrypted image cache unavailable");
        if(s.offers.size()>=128)throw new IOException("Too many pending file offers");
        var e=outgoing(peer);byte[] bytes=imageCache.read(reference);
        try {e.size=bytes.length;e.sha256=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException ex){throw new IOException("SHA-256 unavailable",ex);}
        finally{Arrays.fill(bytes,(byte)0);}
        e.kind="IMAGE";e.fileName="clipboard.png";e.path=reference;
        e.token=UUID.randomUUID().toString()+UUID.randomUUID();e.expires=System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(30);
        s.offers.put(e.id,new Offer(e));return sendEntry(s,peer,e);
    }
    private InputStream attachmentInput(ChatEntry e) throws IOException {
        if(EncryptedImageCache.isReference(e.path)) {
            if(imageCache==null)throw new IOException("Encrypted image cache unavailable");
            byte[] bytes=imageCache.read(e.path);
            if(bytes.length!=e.size){Arrays.fill(bytes,(byte)0);throw new IOException("Cached image changed");}
            return new ByteArrayInputStream(bytes){@Override public void close(){Arrays.fill(buf,(byte)0);}};
        }
        Path file=Path.of(e.path);if(Files.size(file)!=e.size)throw new IOException("Source file changed");
        return Files.newInputStream(file);
    }
    public synchronized CompletableFuture<Void> accept(ChatEntry entry,Path target,boolean replace) throws IOException {
        var s=require();var e=repository.find(entry.id);
        if(e==null||e.outgoing||!e.attachment()||!Set.of("OFFERED","FAILED","INTERRUPTED").contains(e.status))throw new IOException("Attachment cannot be accepted");
        if(e.expires<System.currentTimeMillis())throw new IOException("Offer expired; ask sender to resend");
        var done=new CompletableFuture<Void>();s.pending.put(e.id,done);state(s,e,"QUEUED","");
        try{s.downloads.execute(()->{
            boolean acquired=false;
            try{s.transferSlots.acquire();acquired=true;download(s,e,target.toAbsolutePath(),replace);done.complete(null);}
            catch(Exception failure){try{state(s,e,s.cancelled.contains(e.id)?"CANCELLED":"FAILED",safeError(failure));}finally{done.completeExceptionally(failure);}}
            finally{if(acquired)s.transferSlots.release();s.pending.remove(e.id,done);}
        });}catch(RejectedExecutionException ex){try{state(s,e,"FAILED","Transfer queue is full");}finally{s.pending.remove(e.id,done);done.completeExceptionally(ex);}}
        return done;
    }
    private JSONObject transferRequest(Session s,String op,ChatEntry e){var f=frame(s,op);f.put("to",e.peerId);f.put("id",e.id);f.put("token",e.token);return f;}
    private void download(Session s,ChatEntry e,Path target,boolean replace) throws Exception {
        check(s,e.id);if(!replace&&Files.exists(target))throw new IOException("Destination already exists");
        Path parent=target.getParent();if(parent==null||!Files.isDirectory(parent))throw new IOException("Destination directory missing");
        Path temporary=Files.createTempFile(parent,".opencgl-receive-",".part");Socket socket=null;
        try{
            socket=connect(s,peer(e.peerId));s.transfers.put(e.id,socket);check(s,e.id);
            Wire.write(socket.getOutputStream(),transferRequest(s,"GET",e));var ready=Wire.read(socket.getInputStream());
            if(!"READY".equals(ready.getString("op"))||!e.id.equals(ready.getString("id")))throw new IOException("Download refused: "+ready.getString("error"));
            var digest=MessageDigest.getInstance("SHA-256");state(s,e,"TRANSFERRING","");long start=System.nanoTime(),last=0,total=0;
            try(var output=Files.newOutputStream(temporary)){
                byte[] buffer=new byte[64*1024];while(total<e.size){check(s,e.id);int count=socket.getInputStream().read(buffer,0,(int)Math.min(buffer.length,e.size-total));
                    if(count<0)throw new EOFException("Truncated file");output.write(buffer,0,count);digest.update(buffer,0,count);total+=count;
                    long now=System.nanoTime();if(now-last>TimeUnit.MILLISECONDS.toNanos(150)){progress(s,e,total,start);last=now;}}
            }
            var end=Wire.read(socket.getInputStream());
            if(!"END".equals(end.getString("op"))||!e.sha256.equals(HexFormat.of().formatHex(digest.digest())))throw new IOException("File changed or SHA-256 mismatch");
            synchronized(this){
                check(s,e.id);secure.peerId(socket);
                if(replace)Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);else Files.move(temporary,target);
                e.path=target.toString();state(s,e,"COMPLETED","");
            }
            progress(s,e,e.size,start);
            var ack=frame(s,"FILE_ACK");ack.put("id",e.id);try{Wire.write(socket.getOutputStream(),ack);}catch(IOException ignored){/* local verified file remains valid */}
        }finally{try{Files.deleteIfExists(temporary);}finally{if(socket!=null){s.transfers.remove(e.id,socket);closeSocket(s,socket);}}}
    }
    private void upload(Session s,Socket socket,JSONObject f,Peer peer) throws Exception {
        String messageId=Wire.required(f,"id",64);Offer offer=s.offers.get(messageId);
        if(offer==null||!offer.entry.peerId.equals(peer.id())||!offer.entry.token.equals(f.getString("token"))||offer.entry.expires<System.currentTimeMillis())throw new IOException("Invalid or expired transfer token");
        if(!s.transferSlots.tryAcquire())throw new IOException("Sender busy; retry later");
        if(!offer.claimed.compareAndSet(false,true)){s.transferSlots.release();throw new IOException("Transfer token already used");}
        ChatEntry e=offer.entry.copy();s.transfers.put(e.id,socket);
        try(var input=attachmentInput(e)){
            synchronized(this){check(s,e.id);secure.peerId(socket);}
            state(s,e,"TRANSFERRING","");var ready=frame(s,"READY");ready.put("id",e.id);Wire.write(socket.getOutputStream(),ready);
            var digest=MessageDigest.getInstance("SHA-256");long total=0,start=System.nanoTime(),last=0;
            {byte[] buffer=new byte[64*1024];while(total<e.size){check(s,e.id);int count=input.read(buffer,0,(int)Math.min(buffer.length,e.size-total));
                if(count<0)throw new EOFException("Source file shortened");socket.getOutputStream().write(buffer,0,count);digest.update(buffer,0,count);total+=count;
                long now=System.nanoTime();if(now-last>TimeUnit.MILLISECONDS.toNanos(150)){progress(s,e,total,start);last=now;}}
                if(input.read()!=-1)throw new IOException("Source file grew");}
            if(!e.sha256.equals(HexFormat.of().formatHex(digest.digest())))throw new IOException("Source file changed");
            Wire.write(socket.getOutputStream(),frame(s,"END"));var ack=Wire.read(socket.getInputStream());
            if(!"FILE_ACK".equals(ack.getString("op"))||!e.id.equals(ack.getString("id")))throw new IOException("File receipt missing");
            state(s,e,"COMPLETED","");progress(s,e,e.size,start);
        }catch(Exception failure){state(s,e,s.cancelled.contains(e.id)?"CANCELLED":"FAILED",safeError(failure));throw failure;}
        finally{s.offers.remove(e.id);s.transfers.remove(e.id,socket);s.transferSlots.release();}
    }
    private void check(Session s,String id) throws IOException {if(!s.open||session!=s||s.cancelled.contains(id)||Thread.currentThread().isInterrupted())throw new IOException("Transfer cancelled or service stopped");}
    public void reject(ChatEntry e) throws IOException {var s=require();cancelLocal(s,e,"REJECTED");exchange(s,peer(e.peerId),transferRequest(s,"REJECT",e));}
    public void cancel(String id) throws IOException {
        var s=require();var e=repository.find(id);if(e==null||!e.attachment())return;cancelLocal(s,e,"CANCELLED");
        try{exchange(s,peer(e.peerId),transferRequest(s,"CANCEL",e));}catch(IOException ignored){/* local cancellation is still effective */}
    }
    private synchronized void cancelLocal(Session s,ChatEntry e,String status){
        if(session!=s)return;
        var current=repository.find(e.id);if(current==null)return;e=current;
        if(Set.of("COMPLETED","REJECTED","CANCELLED").contains(e.status))return;
        s.cancelled.add(e.id);s.offers.remove(e.id);var socket=s.transfers.remove(e.id);if(socket!=null)closeSocket(s,socket);state(s,e,status,"");
        var done=s.pending.remove(e.id);if(done!=null)done.completeExceptionally(new IOException("Transfer cancelled"));
    }
    private synchronized void state(Session s,ChatEntry e,String status,String error){
        if(session!=s||!s.open)return;
        var existing=repository.find(e.id);
        if(existing!=null&&existing.status.equals("COMPLETED"))return;
        if(existing!=null&&Set.of("CANCELLED","REJECTED").contains(existing.status)&&!status.equals("SENDING"))return;
        e.status=status;e.error=error;repository.save(e);emit(s,e);
    }
    private static void notifySafely(Runnable callback){try{callback.run();}catch(RuntimeException ignored){/* UI callbacks must not change transport success. */}}
    private synchronized void notifyPeer(Session s,Peer p){if(session==s&&s.open)notifySafely(()->peerListener.accept(p));}
    private synchronized void emit(Session s,ChatEntry e){if(session==s&&s.open){var saved=repository.find(e.id);if(saved!=null)notifySafely(()->messageListener.accept(saved));}}
    private synchronized void progress(Session s,ChatEntry e,long bytes,long start){if(session==s&&s.open)notifySafely(()->progressListener.accept(e.id,new Progress(bytes,e.size,Math.max(1,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)))));}
    public static String hash(Path file,BooleanSupplier cancelled) throws IOException {
        try{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");digest.update(b,0,n);}}return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void discoveryLoop(Session s){
        byte[] bytes=new byte[4096];while(s.open)try{
            var packet=new DatagramPacket(bytes,bytes.length);s.udp.receive(packet);
            if(packet.getLength()>=bytes.length)continue;JSONObject f=JSON.parseObject(Arrays.copyOf(bytes,packet.getLength()));
            if(f.getIntValue("v")!=2||id.equals(f.getString("device")))continue;
            String op=f.getString("op");if(!Set.of("DISCOVER","ANNOUNCE","OFFLINE").contains(op))continue;
            Peer peer=readPeer(f,packet.getAddress().getHostAddress());
            // Discovery is only an untrusted hint: never overwrite authenticated endpoints or persist UUID claims.
            synchronized(this){
                if(session!=s)continue;
                if(!s.authenticated.contains(peer.id())){
                    if("OFFLINE".equals(op)){s.seen.remove(peer.id());s.peers.remove(peer.id());notifyPeer(s,new Peer(peer.id(),peer.name(),peer.ip(),peer.port(),false));}
                    else{s.peers.put(peer.id(),peer);s.seen.put(peer.id(),System.nanoTime());notifyPeer(s,peer);}
                }else{
                    Peer known=s.peers.get(peer.id());
                    // Presence remains an UNVERIFIED hint, never an identity/endpoint update or authorization.
                    if(!"OFFLINE".equals(op)&&known!=null&&known.ip().equals(peer.ip())&&known.port()==peer.port()){
                        Long previous=s.seen.put(peer.id(),System.nanoTime());if(previous==null)notifyPeer(s,known);
                    }
                }
            }
            if("DISCOVER".equals(op)){byte[] reply=JSON.toJSONBytes(frame(s,"ANNOUNCE"));s.udp.send(new DatagramPacket(reply,reply.length,packet.getAddress(),packet.getPort()));}
        }catch(Exception ignored){/* malformed broadcasts cannot terminate discovery */}
    }
    public void refresh() throws IOException {announce(require(),"DISCOVER");}
    private void announce(Session s,String op){
        if(!s.open)return;
        NetworkAddresses.list().stream().filter(a->a.ip().equals(s.ip)&&!a.broadcast().isEmpty()).findFirst().ifPresent(a->{
            try{byte[] bytes=JSON.toJSONBytes(frame(s,op));s.udp.send(new DatagramPacket(bytes,bytes.length,InetAddress.getByName(a.broadcast()),s.udp.getLocalPort()));}
            catch(IOException ex){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"LAN discovery: "+ex.getMessage());}
        });
    }
    private synchronized void expire(Session s){
        if(session!=s)return;long now=System.nanoTime();
        s.seen.forEach((id,time)->{if(now-time>TimeUnit.SECONDS.toNanos(90)&&s.seen.remove(id,time)){Peer p=s.authenticated.contains(id)?s.peers.get(id):s.peers.remove(id);if(p!=null)notifyPeer(s,new Peer(p.id(),p.name(),p.ip(),p.port(),false));}});
        s.offers.forEach((id,offer)->{if(offer.entry.expires<System.currentTimeMillis()&&!offer.claimed.get()&&s.offers.remove(id,offer))state(s,offer.entry.copy(),"FAILED","Offer expired");});
    }
    private static String safeError(Throwable e){String text=e.getMessage();return text==null?e.getClass().getSimpleName():text.substring(0,Math.min(500,text.length()));}
    private void closeSocket(Session s,Socket socket){s.sockets.remove(socket);var timer=s.deadlines.remove(socket);if(timer!=null)timer.cancel(false);secure.release(socket);}
    public synchronized void revokeTrust(String peerId) throws IOException {
        secure.revoke(peerId);
        synchronized(this){var s=session;if(s==null)return;s.authenticated.remove(peerId);
            s.offers.values().stream().map(o->o.entry).filter(e->peerId.equals(e.peerId)).toList().forEach(e->cancelLocal(s,e,"CANCELLED"));
            s.pending.keySet().stream().map(repository::find).filter(Objects::nonNull).filter(e->peerId.equals(e.peerId)).toList().forEach(e->cancelLocal(s,e,"CANCELLED"));
        }
    }
    public synchronized void stop(){
        var s=session;if(s==null)return;announce(s,"OFFLINE");session=null;s.open=false;
        try{s.tcp.close();}catch(IOException ignored){}s.udp.close();s.sockets.forEach(socket->closeSocket(s,socket));
        s.timer.shutdownNow();s.workers.shutdownNow();s.downloads.shutdownNow();s.offers.clear();
        s.pending.values().forEach(f->f.completeExceptionally(new IOException("Service stopped")));s.pending.clear();repository.markInterrupted();
    }
    @Override public void close(){stop();secure.close();}
}
