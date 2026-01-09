package com.opencgl.gitlite.service;

import java.util.*;

/**
 * .gitignore 模板服务
 */
public class GitIgnoreService {
    
    private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();
    
    static {
        // Java
        TEMPLATES.put("Java", """
            # Compiled class file
            *.class
            
            # Log file
            *.log
            
            # BlueJ files
            *.ctxt
            
            # Mobile Tools for Java (J2ME)
            .mtj.tmp/
            
            # Package Files
            *.jar
            *.war
            *.nar
            *.ear
            *.zip
            *.tar.gz
            *.rar
            
            # virtual machine crash logs
            hs_err_pid*
            replay_pid*
            
            # Maven
            target/
            pom.xml.tag
            pom.xml.releaseBackup
            pom.xml.versionsBackup
            pom.xml.next
            release.properties
            
            # Gradle
            .gradle/
            build/
            
            # IDE
            .idea/
            *.iml
            .eclipse/
            .settings/
            .project
            .classpath
            """);
        
        // Python
        TEMPLATES.put("Python", """
            # Byte-compiled / optimized / DLL files
            __pycache__/
            *.py[cod]
            *$py.class
            
            # C extensions
            *.so
            
            # Distribution / packaging
            .Python
            build/
            develop-eggs/
            dist/
            downloads/
            eggs/
            .eggs/
            lib/
            lib64/
            parts/
            sdist/
            var/
            wheels/
            *.egg-info/
            .installed.cfg
            *.egg
            
            # PyInstaller
            *.manifest
            *.spec
            
            # Installer logs
            pip-log.txt
            pip-delete-this-directory.txt
            
            # Virtual environments
            .env
            .venv
            env/
            venv/
            ENV/
            
            # IDE
            .idea/
            .vscode/
            *.swp
            *.swo
            """);
        
        // Node.js
        TEMPLATES.put("Node.js", """
            # Dependencies
            node_modules/
            
            # Build output
            dist/
            build/
            
            # Logs
            logs
            *.log
            npm-debug.log*
            yarn-debug.log*
            yarn-error.log*
            
            # Runtime data
            pids
            *.pid
            *.seed
            *.pid.lock
            
            # Coverage
            coverage/
            .nyc_output
            
            # Environment
            .env
            .env.local
            .env.*.local
            
            # IDE
            .idea/
            .vscode/
            *.swp
            
            # OS
            .DS_Store
            Thumbs.db
            """);
        
        // Go
        TEMPLATES.put("Go", """
            # Binaries
            *.exe
            *.exe~
            *.dll
            *.so
            *.dylib
            
            # Test binary
            *.test
            
            # Output of go coverage
            *.out
            
            # Go workspace
            go.work
            
            # Vendor
            vendor/
            
            # IDE
            .idea/
            .vscode/
            """);
        
        // macOS
        TEMPLATES.put("macOS", """
            # General
            .DS_Store
            .AppleDouble
            .LSOverride
            
            # Icon must end with two \\r
            Icon
            
            # Thumbnails
            ._*
            
            # Files that might appear in the root of a volume
            .DocumentRevisions-V100
            .fseventsd
            .Spotlight-V100
            .TemporaryItems
            .Trashes
            .VolumeIcon.icns
            .com.apple.timemachine.donotpresent
            
            # Directories potentially created on remote AFP share
            .AppleDB
            .AppleDesktop
            Network Trash Folder
            Temporary Items
            .apdisk
            """);
        
        // Windows
        TEMPLATES.put("Windows", """
            # Windows thumbnail cache files
            Thumbs.db
            Thumbs.db:encryptable
            ehthumbs.db
            ehthumbs_vista.db
            
            # Dump file
            *.stackdump
            
            # Folder config file
            [Dd]esktop.ini
            
            # Recycle Bin used on file shares
            $RECYCLE.BIN/
            
            # Windows Installer files
            *.cab
            *.msi
            *.msix
            *.msm
            *.msp
            
            # Windows shortcuts
            *.lnk
            """);
        
        // IDE - IntelliJ
        TEMPLATES.put("IntelliJ IDEA", """
            # IntelliJ IDEA
            .idea/
            *.iml
            *.ipr
            *.iws
            out/
            
            # CMake
            cmake-build-*/
            
            # File-based project format
            *.iws
            """);
        
        // IDE - VSCode
        TEMPLATES.put("VS Code", """
            .vscode/*
            !.vscode/settings.json
            !.vscode/tasks.json
            !.vscode/launch.json
            !.vscode/extensions.json
            *.code-workspace
            """);
    }
    
    public List<String> getAvailableTemplates() {
        return new ArrayList<>(TEMPLATES.keySet());
    }
    
    public String getTemplate(String name) {
        return TEMPLATES.getOrDefault(name, "");
    }
    
    public String combineTemplates(List<String> templateNames) {
        StringBuilder sb = new StringBuilder();
        for (String name : templateNames) {
            String template = TEMPLATES.get(name);
            if (template != null) {
                sb.append("# ").append(name).append("\n");
                sb.append(template).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}
