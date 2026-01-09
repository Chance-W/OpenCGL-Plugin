// 默认脚本: JSON 格式化
// 格式化响应 JSON 便于阅读

function preProcess(json, context) {
    return json;
}

function postProcess(json, context) {
    try {
        var data = JSON.parse(json);
        return JSON.stringify(data, null, 2);
    } catch (e) {
        return json;
    }
}

function configureTls(context) {
    return null;
}

function getName() {
    return "JSON格式化";
}
