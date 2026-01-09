package com.opencgl.doctomd.service;

import com.opencgl.doctomd.model.FileInfo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与 C++ doc-converter 一致的转换逻辑：调用系统 pdftotext/pdfimages/pandoc/soffice/tesseract，
 * 并在代码内做 DOCX 后处理（路径替换、删除图片扩展属性）。
 */
public class DocToMarkdownService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");

    private Consumer<String> log = s -> {};
    private volatile Process currentProcess;

    public void setLog(Consumer<String> log) {
        this.log = log != null ? log : (s -> {});
    }

    private void log(String msg) {
        log.accept(msg);
    }

    /** 检测系统是否包含某命令（which/where） */
    public boolean checkTool(String command) {
        try {
            ProcessBuilder pb = IS_WINDOWS
                ? new ProcessBuilder("cmd", "/c", "where", command)
                : new ProcessBuilder("sh", "-c", "which " + command + " 2>/dev/null");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            currentProcess = p;
            String out = readFully(p.getInputStream(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            currentProcess = null;
            return code == 0 && out != null && !out.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取当前系统的安装提示（用于日志） */
    public String getInstallHint(String tool) {
        if (IS_WINDOWS) return getInstallHintWindows(tool);
        if (isMac()) return getInstallHintMac(tool);
        return getInstallHintLinux(tool);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String getInstallHintMac(String tool) {
        switch (tool) {
            case "pdftotext":
            case "pdfimages":
                return "brew install poppler";
            case "pandoc":
                return "brew install pandoc";
            case "soffice":
                return "brew install libreoffice";
            case "tesseract":
                return "brew install tesseract tesseract-lang";
            default:
                return "";
        }
    }

    private static String getInstallHintLinux(String tool) {
        switch (tool) {
            case "pdftotext":
            case "pdfimages":
                return "sudo apt-get install poppler-utils  (Fedora: sudo dnf install poppler-utils)";
            case "pandoc":
                return "sudo apt-get install pandoc  (Fedora: sudo dnf install pandoc)";
            case "soffice":
                return "sudo apt-get install libreoffice  (Fedora: sudo dnf install libreoffice)";
            case "tesseract":
                return "sudo apt-get install tesseract-ocr tesseract-ocr-chi-sim  (Fedora: sudo dnf install tesseract tesseract-langpack-chi_sim)";
            default:
                return "";
        }
    }

    private static String getInstallHintWindows(String tool) {
        switch (tool) {
            case "pdftotext":
            case "pdfimages":
                return "https://github.com/oschwartz10612/poppler-windows/releases 或 MSYS2: pacman -S mingw-w64-x86_64-poppler";
            case "pandoc":
                return "https://pandoc.org/installing.html 或 MSYS2: pacman -S mingw-w64-x86_64-pandoc";
            case "soffice":
                return "https://www.libreoffice.org/download/ 或 https://mirrors.tuna.tsinghua.edu.cn/libreoffice/libreoffice/stable/";
            case "tesseract":
                return "https://github.com/UB-Mannheim/tesseract/wiki 或 MSYS2: pacman -S mingw-w64-x86_64-tesseract-ocr";
            default:
                return "";
        }
    }

    /** 返回各系统安装说明（用于缺失依赖时在日志/弹窗中展示） */
    public String getInstallHintsAllPlatforms(String tool) {
        String mac = getInstallHintMac(tool);
        String linux = getInstallHintLinux(tool);
        String win = getInstallHintWindows(tool);
        if (mac.isEmpty() && linux.isEmpty() && win.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (!mac.isEmpty()) sb.append("macOS: ").append(mac).append("\n");
        if (!linux.isEmpty()) sb.append("Linux: ").append(linux).append("\n");
        if (!win.isEmpty()) sb.append("Windows: ").append(win);
        return sb.toString();
    }

    /**
     * 执行转换。返回是否成功。
     * @param stepCompleted 每完成一个步骤调用一次（可阻塞等待 UI 更新），可为 null
     */
    public boolean convert(FileInfo fileInfo, boolean extractImages, boolean useRelativePath, boolean useOCR,
                          Consumer<String> statusUpdater, Runnable stepCompleted) {
        String inputFile = fileInfo.getFilePath();
        String outputDir = fileInfo.getOutputDir();
        String mediaDir = fileInfo.getMediaDir();
        if (mediaDir == null || mediaDir.isEmpty()) mediaDir = "media";

        Path outPath = Paths.get(outputDir);
        try {
            Files.createDirectories(outPath);
        } catch (IOException e) {
            log("  " + e.getMessage());
            return false;
        }

        String baseName = baseName(inputFile);
        String outputFile = outPath.resolve(baseName + ".md").toString();
        String ext = getFileExtension(inputFile);

        log("  " + baseName + " -> " + outputDir);
        log("  mediaDir: " + mediaDir);

        boolean result = false;
        if ("pdf".equals(ext)) {
            result = convertPDF(inputFile, outputFile, outputDir, mediaDir, extractImages, useRelativePath, useOCR, stepCompleted);
        } else if ("doc".equals(ext)) {
            result = convertDOC(inputFile, outputFile, outputDir, mediaDir, extractImages, useRelativePath, stepCompleted);
        } else if ("docx".equals(ext)) {
            result = convertDOCX(inputFile, outputFile, outputDir, mediaDir, extractImages, useRelativePath, stepCompleted);
        } else if ("html".equals(ext) || "htm".equals(ext)) {
            result = convertHTML(inputFile, outputFile, stepCompleted);
        } else {
            log("  unsupported: " + ext);
        }
        return result;
    }

    /** 按格式返回该文件转换的步骤数（用于计算总步骤） */
    public static int getStepsForExtension(String ext) {
        if (ext == null) return 1;
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "pdf": return 3;
            case "doc": return 3;
            case "docx": return 2;
            case "html":
            case "htm": return 1;
            default: return 1;
        }
    }

    private boolean convertPDF(String inputFile, String outputFile, String outputDir, String mediaDir,
                               boolean extractImages, boolean useRelativePath, boolean useOCR, Runnable stepCompleted) {
        Path imageDirPath = Paths.get(outputDir, mediaDir);
        String imageDir = imageDirPath.toString();

        if (extractImages) {
            try {
                Files.createDirectories(imageDirPath);
            } catch (IOException e) {
                log("  mkdir failed: " + e.getMessage());
            }
            String outPrefix = imageDir + File.separator + "img";
            int imgRet = runProcess("pdfimages", "-png", inputFile, outPrefix);
            log("  pdfimages exit: " + imgRet);
        }
        if (stepCompleted != null) stepCompleted.run();

        // pdftotext file -  (stdout)
        StringBuilder text = new StringBuilder();
        int code = runProcessRedirectOut(text, "pdftotext", inputFile, "-");
        if (code != 0) {
            log("  pdftotext failed: " + code);
            return false;
        }
        String extractedText = text.toString();
        log("  text length: " + extractedText.length());

        boolean needOCR = useOCR && extractedText.length() < 100;
        String ocrText = "";
        if (needOCR && extractImages && checkTool("tesseract")) {
            log("  OCR (chi_sim+eng)...");
            File idir = new File(imageDir);
            File[] pngs = idir.listFiles((d, n) -> n != null && n.endsWith(".png"));
            if (pngs != null) {
                for (int i = 0; i < pngs.length; i++) {
                    String imgPath = pngs[i].getAbsolutePath();
                    String tmpOut = imageDir + File.separator + "ocr_temp_" + i;
                    runProcess("tesseract", imgPath, tmpOut, "-l", "chi_sim+eng", "--psm", "3");
                    String txtPath = tmpOut + ".txt";
                    try {
                        ocrText += Files.readString(Paths.get(txtPath), StandardCharsets.UTF_8) + "\n\n";
                        Files.deleteIfExists(Paths.get(txtPath));
                    } catch (IOException ignored) {}
                }
            }
        }
        if (stepCompleted != null) stepCompleted.run();

        try (PrintWriter out = new PrintWriter(outputFile, StandardCharsets.UTF_8)) {
            out.println("# " + baseName(inputFile));
            out.println();
            if (!ocrText.isEmpty()) {
                out.println("## OCR");
                out.println();
                out.print(ocrText);
            } else if (!extractedText.isEmpty()) {
                out.print(extractedText);
            }
            if (extractImages) {
                File idir = new File(imageDir);
                File[] pngs = idir.listFiles((d, n) -> n != null && n.endsWith(".png"));
                if (pngs != null && pngs.length > 0) {
                    out.println();
                    out.println();
                    out.println("## 图片");
                    out.println();
                    Arrays.sort(pngs, Comparator.comparing(File::getName));
                    for (int i = 0; i < pngs.length; i++) {
                        if (useRelativePath) {
                            out.println("![image" + i + "](" + mediaDir + "/" + pngs[i].getName() + ")");
                        } else {
                            String abs = pngs[i].getAbsolutePath().replace("\\", "/");
                            out.println("![image" + i + "](" + abs + ")");
                        }
                        out.println();
                    }
                }
            }
        } catch (IOException e) {
            log("  write failed: " + e.getMessage());
            return false;
        }
        if (stepCompleted != null) stepCompleted.run();
        return true;
    }

    private boolean convertDOC(String inputFile, String outputFile, String outputDir, String mediaDir,
                               boolean extractImages, boolean useRelativePath, Runnable stepCompleted) {
        log("  DOC -> DOCX (soffice)...");
        if (!checkTool("soffice")) {
            log("  soffice not found");
            return false;
        }
        int ret = runProcess("soffice", "--headless", "--convert-to", "docx", inputFile, "--outdir", outputDir);
        if (ret != 0) return false;
        String baseName = baseName(inputFile);
        String docxFile = outputDir + File.separator + baseName + ".docx";
        if (!Files.exists(Paths.get(docxFile))) {
            log("  docx not found: " + docxFile);
            return false;
        }
        if (stepCompleted != null) stepCompleted.run();
        log("  DOCX -> MD...");
        return convertDOCX(docxFile, outputFile, outputDir, mediaDir, extractImages, useRelativePath, stepCompleted);
    }

    private boolean convertDOCX(String inputFile, String outputFile, String outputDir, String mediaDir,
                                boolean extractImages, boolean useRelativePath, Runnable stepCompleted) {
        List<String> cmd = new ArrayList<>();
        cmd.add("pandoc");
        cmd.add(inputFile);
        cmd.add("-t");
        cmd.add("markdown");
        cmd.add("-o");
        cmd.add(outputFile);
        if (extractImages) {
            cmd.add("--extract-media=" + outputDir);
        }
        int ret = runProcess(cmd.toArray(new String[0]));
        if (ret != 0) {
            log("  pandoc failed");
            return false;
        }
        if (stepCompleted != null) stepCompleted.run();

        if (extractImages) {
            Path defaultMedia = Paths.get(outputDir, "media");
            Path customMedia = Paths.get(outputDir, mediaDir);
            if (!"media".equals(mediaDir) && Files.isDirectory(defaultMedia)) {
                try {
                    if (Files.exists(customMedia)) deleteRecursively(customMedia);
                    Files.move(defaultMedia, customMedia);
                } catch (IOException e) {
                    log("  rename media: " + e.getMessage());
                }
            }
        }

        if (extractImages && useRelativePath) {
            try {
                String content = Files.readString(Paths.get(outputFile), StandardCharsets.UTF_8);
                content = postProcessDocxMarkdown(content, outputDir, mediaDir);
                Files.writeString(Paths.get(outputFile), content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log("  post-process: " + e.getMessage());
            }
        }
        if (stepCompleted != null) stepCompleted.run();
        return true;
    }

    /**
     * 与 C++ 一致：去掉输出目录前缀、media/ -> mediaDir/、删除 ){ ... } 扩展属性。
     */
    public String postProcessDocxMarkdown(String content, String outputDir, String mediaDir) {
        // 去掉 outputDir 的绝对路径（Unix 与 Windows 两种）
        String outUnix = outputDir.replace("\\", "/");
        if (!outUnix.endsWith("/")) outUnix += "/";
        String outNative = outputDir.replace("/", File.separator);
        if (!outNative.endsWith(File.separator)) outNative += File.separator;
        content = content.replace(outUnix, "");
        content = content.replace(outNative, "");
        if (!"media".equals(mediaDir)) {
            content = content.replace("media/", mediaDir + "/");
        }
        // 删除 ) 后面的 { ... }
        Pattern p = Pattern.compile("(!\\[.*?\\]\\([^)]*\\))\\s*\\{[^}]*\\}");
        Matcher m = p.matcher(content);
        content = m.replaceAll("$1");
        return content;
    }

    private boolean convertHTML(String inputFile, String outputFile, Runnable stepCompleted) {
        int ret = runProcess("pandoc", inputFile, "-t", "markdown", "-o", outputFile);
        if (ret == 0 && stepCompleted != null) stepCompleted.run();
        return ret == 0;
    }

    private static String baseName(String path) {
        if (path == null) return "";
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = i < 0 ? path : path.substring(i + 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String getFileExtension(String path) {
        String name = path == null ? "" : path;
        int i = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = i < 0 ? name : name.substring(i + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 对含空格或特殊字符的路径加引号，避免 sh -c / cmd 拆参错误 */
    private static String quote(String s) {
        if (s == null) return "\"\"";
        if (s.isEmpty()) return "\"\"";
        boolean needQuote = s.contains(" ") || s.contains("\t") || s.contains("'") || s.contains("\"");
        if (!needQuote) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 使用 ProcessBuilder 直接传参，支持路径含空格；失败时记录 stderr */
    private int runProcess(String... command) {
        if (command == null || command.length == 0) return -1;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            currentProcess = p;
            int code = p.waitFor();
            currentProcess = null;
            if (code != 0) {
                String err = readFully(p.getErrorStream(), StandardCharsets.UTF_8);
                if (err != null && !err.trim().isEmpty()) log("  stderr: " + err.trim());
            }
            return code;
        } catch (Exception e) {
            log("  run error: " + e.getMessage());
            return -1;
        }
    }

    private int runProcessRedirectOut(StringBuilder out, String... command) {
        if (command == null || command.length == 0) return -1;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            currentProcess = p;
            try (Reader r = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) out.append(buf, 0, n);
            }
            int code = p.waitFor();
            currentProcess = null;
            if (code != 0) {
                String err = readFully(p.getErrorStream(), StandardCharsets.UTF_8);
                if (err != null && !err.trim().isEmpty()) log("  stderr: " + err.trim());
            }
            return code;
        } catch (Exception e) {
            return -1;
        }
    }

    public void cancel() {
        Process process = currentProcess;
        currentProcess = null;
        if (process != null) {
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String readFully(InputStream is, java.nio.charset.Charset cs) throws IOException {
        if (is == null) return "";
        try (Reader r = new InputStreamReader(is, cs)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            return sb.toString();
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
                for (Path child : ds) deleteRecursively(child);
            }
        }
        Files.delete(path);
    }
}
