package com.opencgl.sqlclient.util;

import java.util.ArrayList;
import java.util.List;

/** 按分号拆分 SQL，同时忽略字符串和注释中的分号。 */
public final class SqlStatementSplitter {
    private SqlStatementSplitter() {}
    public static List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        if (sql == null) return result;
        StringBuilder current = new StringBuilder();
        boolean single=false, dbl=false, line=false, block=false;
        for (int i=0;i<sql.length();i++) {
            char c=sql.charAt(i), n=i+1<sql.length()?sql.charAt(i+1):0;
            if (line) { current.append(c); if(c=='\n'||c=='\r') line=false; continue; }
            if (block) { current.append(c); if(c=='*'&&n=='/'){current.append(n);i++;block=false;} continue; }
            if (!dbl&&c=='\'' ){single=!single; current.append(c); continue;}
            if (!single&&c=='"'){dbl=!dbl; current.append(c); continue;}
            if(!single&&!dbl&&c=='-'&&n=='-'){line=true;current.append(c).append(n);i++;continue;}
            if(!single&&!dbl&&c=='/'&&n=='*'){block=true;current.append(c).append(n);i++;continue;}
            if(!single&&!dbl&&c==';'){String s=current.toString().trim();if(!s.isEmpty())result.add(s);current.setLength(0);continue;}
            current.append(c);
        }
        String s=current.toString().trim(); if(!s.isEmpty())result.add(s);
        return result;
    }
}
