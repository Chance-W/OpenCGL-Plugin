#!/bin/bash

# OpenCGL 插件部署验证脚本
echo "=========================================="
echo "  OpenCGL 插件部署验证"
echo "=========================================="
echo ""

BIN_DIR="./bin"
PLUGINS=(
    "HttpDebuggerModule-1.0-SNAPSHOT.jar"
    "MarkdownEditorModule-1.0-SNAPSHOT.jar"
    "ClipboardHistoryModule-1.0-SNAPSHOT.jar"
    "BatchRenamerModule-1.0-SNAPSHOT.jar"
    "PathExtractorModule-1.0-SNAPSHOT.jar"
    "SqlClientModule-1.0-SNAPSHOT.jar"
    "PixelRulerModule-1.0-SNAPSHOT.jar"
    "JavaDecompilerModule-1.0-SNAPSHOT.jar"
)

TOTAL=0
SUCCESS=0
FAILED=0

for plugin in "${PLUGINS[@]}"; do
    ((TOTAL++))
    if [ -f "$BIN_DIR/$plugin" ]; then
        SIZE=$(ls -lh "$BIN_DIR/$plugin" | awk '{print $5}')
        echo "✅ $plugin ($SIZE)"
        ((SUCCESS++))
    else
        echo "❌ $plugin - 未找到"
        ((FAILED++))
    fi
done

echo ""
echo "=========================================="
echo "  验证结果"
echo "=========================================="
echo "总计: $TOTAL 个插件"
echo "成功: $SUCCESS 个"
echo "失败: $FAILED 个"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "🎉 所有插件部署成功！"
    echo ""
    echo "下一步:"
    echo "1. 重启 OpenCGL"
    echo "2. 查看插件是否正确加载"
    echo "3. 测试各个插件功能"
else
    echo "⚠️ 有 $FAILED 个插件缺失，请检查编译日志"
fi

echo ""
