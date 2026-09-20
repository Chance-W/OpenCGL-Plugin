package com.opencgl.decompiler;

import com.github.javaparser.JavaParser;
import com.opencgl.decompiler.service.DecompilerService;
import javassist.bytecode.ClassFile;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class DecompilerSourceTest {
    @Test void diagnosticMessagesNeverContaminateJavaSource() throws Exception {
        var bytes = new ByteArrayOutputStream();
        new ClassFile(false, "Sample", "java.lang.Object").write(new DataOutputStream(bytes));
        var result = new DecompilerService().decompileFromBytes(bytes.toByteArray(), "Sample");
        assertTrue(result.isSuccess());
        assertTrue(new JavaParser().parse(result.getSourceCode()).isSuccessful(), result.getSourceCode());
    }
}
