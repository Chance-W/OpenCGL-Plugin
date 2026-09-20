package com.opencgl.lanmsg.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Bounded decoding off the JavaFX thread. GIF uses its first frame. */
public final class MediaSupport {
    public static final long MAX_PIXELS=25_000_000;
    private MediaSupport() {}
    public static byte[] encodePng(BufferedImage image) throws IOException {
        if(image==null||(long)image.getWidth()*image.getHeight()>MAX_PIXELS)throw new IOException("Image exceeds 25 megapixels");
        var bytes=new java.io.ByteArrayOutputStream();
        var bounded=new java.io.OutputStream(){
            public void write(int b) throws IOException {check(1);bytes.write(b);}
            public void write(byte[] b,int off,int len) throws IOException {check(len);bytes.write(b,off,len);}
            private void check(int length) throws IOException {
                if(length>com.opencgl.lanmsg.security.LocalCipher.MAX_PLAINTEXT_BYTES-bytes.size())throw new IOException("Pasted PNG exceeds 16 MiB; send an ordinary file instead");
            }
        };
        var writers=ImageIO.getImageWritersByFormatName("png");if(!writers.hasNext())throw new IOException("PNG encoder unavailable");
        var writer=writers.next();
        try(var output=new javax.imageio.stream.MemoryCacheImageOutputStream(bounded)) {
            writer.setOutput(output);writer.write(image);output.flush();return bytes.toByteArray();
        }finally{writer.dispose();}
    }
    public static BufferedImage read(Path path,int bound) throws IOException {
        try(var stream=ImageIO.createImageInputStream(path.toFile())) {
            return read(stream,bound);
        }
    }
    /** No ImageIO disk cache: decrypted images exist only in memory. */
    public static BufferedImage read(byte[] bytes,int bound) throws IOException {
        try(var stream=new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            return read(stream,bound);
        }
    }
    private static BufferedImage read(javax.imageio.stream.ImageInputStream stream,int bound) throws IOException {
        if(bound<1||bound>2048)throw new IOException("Invalid image bound");
            if(stream==null)throw new IOException("Image unavailable");
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IOException("Unsupported image");
            var reader=readers.next();
            try {
                reader.setInput(stream,true,true);
                if(!java.util.Set.of("png","jpeg","jpg","gif").contains(reader.getFormatName().toLowerCase(Locale.ROOT)))throw new IOException("Unsupported image format");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0||(long)width*height>MAX_PIXELS)throw new IOException("Image exceeds 25 megapixels");
                var param=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(width,height)/bound);
                param.setSourceSubsampling(sample,sample,0,0);
                var decoded=reader.read(0,param);
                double scale=Math.min(1d,(double)bound/Math.max(decoded.getWidth(),decoded.getHeight()));
                if(scale==1)return decoded;
                var result=new BufferedImage(Math.max(1,(int)(decoded.getWidth()*scale)),Math.max(1,(int)(decoded.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=result.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(decoded,0,0,result.getWidth(),result.getHeight(),null);}finally{g.dispose();decoded.flush();}
                return result;
            }finally{reader.dispose();}
    }
    public static String safeName(String name) {
        String safe=name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replaceAll("[. ]+$","");
        if(safe.isBlank()||safe.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))safe="file_"+safe;
        return safe;
    }
}
