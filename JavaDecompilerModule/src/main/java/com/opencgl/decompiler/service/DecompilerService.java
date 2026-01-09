package com.opencgl.decompiler.service;

import com.opencgl.decompiler.model.DecompileResult;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * CFR反编译服务
 */
public class DecompilerService {
    private static final Logger logger = LoggerFactory.getLogger(DecompilerService.class);

    /**
     * 反编译class字节码（从内存）
     */
    public DecompileResult decompileFromBytes(byte[] classBytes, String className) {
        try {
            final StringBuilder sourceCode = new StringBuilder();
            
            // 配置CFR选项
            Map<String, String> options = new HashMap<>();
            options.put("showversion", "false");           // 不显示版本信息
            options.put("hidelongstrings", "false");  
            options.put("hideutf", "false");
            options.put("innerclasses", "true");
            options.put("skipbatchinnerclasses", "false");
            options.put("commentmonitors", "false");       // 不添加monitor注释
            options.put("comments", "false");              // 不添加额外注释
            options.put("hidebridgemethods", "true");      // 隐藏桥接方法
            options.put("removedeadmethods", "false");
            options.put("removebadidentifiers", "false");
            options.put("sugarenums", "true");             // 美化枚举
            options.put("sugarasserts", "true");           // 美化断言
            options.put("arrayiter", "true");              // 美化数组迭代
            options.put("collectioniter", "true");         // 美化集合迭代

            // 创建内存中的ClassFileSource
            ClassFileSource classFileSource = new ClassFileSource() {
                @Override
                public void informAnalysisRelativePathDetail(String usedPath, String specPath) {
                }

                @Override
                public Collection<String> addJar(String jarPath) {
                    return Collections.emptyList();
                }

                @Override
                public String getPossiblyRenamedPath(String path) {
                    return path;
                }

                @Override
                public Pair<byte[], String> getClassFileContent(String path) throws IOException {
                    // CFR会请求这个路径，可能带或不带.class后缀
                    logger.debug("CFR请求类文件: {}", path);
                    
                    // 规范化路径（移除.class后缀用于比较）
                    String normalizedPath = path;
                    if (normalizedPath.endsWith(".class")) {
                        normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 6);
                    }
                    
                    String normalizedClassName = className;
                    if (normalizedClassName.endsWith(".class")) {
                        normalizedClassName = normalizedClassName.substring(0, normalizedClassName.length() - 6);
                    }
                    
                    if (normalizedPath.equals(normalizedClassName)) {
                        logger.debug("返回类字节码，长度: {}", classBytes.length);
                        return Pair.make(classBytes, path);
                    }
                    
                    logger.debug("路径不匹配: {} vs {}", normalizedPath, normalizedClassName);
                    return null;
                }
            };

            // 创建输出接收器
            OutputSinkFactory mySink = new OutputSinkFactory() {
                @Override
                public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                    // 支持所有类型的输出
                    return new ArrayList<>(available);
                }

                @Override
                public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                    return new Sink<T>() {
                        @Override
                        public void write(T sinkable) {
                            // 捕获反编译结果
                            if (sinkable instanceof String) {
                                String content = (String) sinkable;
                                logger.debug("CFR输出(String): {}", content.substring(0, Math.min(100, content.length())));
                                sourceCode.append(content);
                            } 
                            else if (sinkable instanceof org.benf.cfr.reader.api.SinkReturns.Decompiled) {
                                org.benf.cfr.reader.api.SinkReturns.Decompiled decompiled = 
                                    (org.benf.cfr.reader.api.SinkReturns.Decompiled) sinkable;
                                String java = decompiled.getJava();
                                logger.debug("CFR输出(Decompiled): {}", java.substring(0, Math.min(100, java.length())));
                                sourceCode.append(java);
                            }
                        }
                    };
                }
            };

            // 创建CFR驱动
            CfrDriver driver = new CfrDriver.Builder()
                    .withOptions(options)
                    .withOutputSink(mySink)
                    .withClassFileSource(classFileSource)
                    .build();

            logger.info("开始反编译类: {}", className);
            
            // 反编译
            driver.analyse(Collections.singletonList(className));

            String result = sourceCode.toString();
            logger.info("反编译完成，结果长度: {}", result.length());
            
            if (result.isEmpty()) {
                logger.error("反编译结果为空，className: {}, bytesLength: {}", className, classBytes.length);
                return DecompileResult.failure("反编译结果为空，可能是类名格式不正确");
            }
            
            return DecompileResult.success(result);
            
        } catch (Exception e) {
            logger.error("反编译失败: " + className, e);
            return DecompileResult.failure("反编译失败: " + e.getMessage());
        }
    }
}
