package com.opencgl.decompiler;

import com.opencgl.decompiler.model.ClassNode;
import com.opencgl.decompiler.service.SymbolIndexService;
import javassist.bytecode.ClassFile;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SymbolIndexServiceTest {
    @Test void indexReadsQualifiedNamesAndFinishesForEmptyWorkspace() throws Exception {
        var bytes = new ByteArrayOutputStream();
        new ClassFile(false, "p.RealName", "java.lang.Object").write(new DataOutputStream(bytes));
        var node = new ClassNode("WrongName.class", "BOOT-INF/classes/p/RealName.class", true, false);
        node.setClassBytes(bytes.toByteArray());
        var service = new SymbolIndexService();
        List<String> progress = new ArrayList<>();
        service.index(List.of(node), (done, total) -> progress.add(done + "/" + total));
        assertEquals("p.RealName", service.entries().get(0).qualifiedName());
        assertEquals(List.of(node), service.findClass("p.RealName"));
        assertEquals("1/1", progress.get(progress.size()-1));
        service.index(List.of(), (done, total) -> progress.add(done + "/" + total));
        assertTrue(service.entries().isEmpty());
        assertEquals("0/0", progress.get(progress.size()-1));
    }
}
