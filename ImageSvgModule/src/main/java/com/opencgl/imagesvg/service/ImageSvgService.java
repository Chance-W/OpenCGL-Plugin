package com.opencgl.imagesvg.service;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

/**
 * 图片转SVG服务
 */
public class ImageSvgService {

    private static final Logger logger = LoggerFactory.getLogger(ImageSvgService.class);

    /**
     * 将图片转换为嵌入Base64的SVG
     */
    public String convertToEmbeddedSvg(File imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("无法读取图片文件");
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 获取图片格式
        String fileName = imageFile.getName().toLowerCase();
        String mimeType = "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            mimeType = "image/jpeg";
        } else if (fileName.endsWith(".gif")) {
            mimeType = "image/gif";
        } else if (fileName.endsWith(".webp")) {
            mimeType = "image/webp";
        }
        
        // 读取文件并转Base64
        byte[] imageBytes = java.nio.file.Files.readAllBytes(imageFile.toPath());
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        
        // 生成SVG
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        svg.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ");
        svg.append("width=\"").append(width).append("\" ");
        svg.append("height=\"").append(height).append("\" ");
        svg.append("viewBox=\"0 0 ").append(width).append(" ").append(height).append("\">\n");
        svg.append("  <image width=\"").append(width).append("\" height=\"").append(height).append("\" ");
        svg.append("xlink:href=\"data:").append(mimeType).append(";base64,").append(base64).append("\"/>\n");
        svg.append("</svg>");
        
        return svg.toString();
    }

    /**
     * 将图片转换为矢量化SVG (简单像素追踪)
     */
    public String convertToTracedSvg(File imageFile, int threshold) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("无法读取图片文件");
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        svg.append("width=\"").append(width).append("\" ");
        svg.append("height=\"").append(height).append("\" ");
        svg.append("viewBox=\"0 0 ").append(width).append(" ").append(height).append("\">\n");
        
        // 简单的像素到矩形转换
        int pixelSize = Math.max(1, Math.min(width, height) / 100);
        for (int y = 0; y < height; y += pixelSize) {
            for (int x = 0; x < width; x += pixelSize) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int a = (rgb >> 24) & 0xFF;
                
                if (a > threshold) {
                    String color = String.format("#%02x%02x%02x", r, g, b);
                    svg.append("  <rect x=\"").append(x).append("\" y=\"").append(y);
                    svg.append("\" width=\"").append(pixelSize).append("\" height=\"").append(pixelSize);
                    svg.append("\" fill=\"").append(color).append("\"/>\n");
                }
            }
        }
        
        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * 保存SVG到文件
     */
    public void saveSvg(String svgContent, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(svgContent);
        }
    }
}
