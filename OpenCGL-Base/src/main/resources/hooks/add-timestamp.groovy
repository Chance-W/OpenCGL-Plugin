// 默认脚本: 添加时间戳
// 在请求 JSON 中添加 timestamp 字段

import groovy.json.*

def preProcess(String json, context) {
    try {
        def data = new JsonSlurper().parseText(json)
        data.timestamp = System.currentTimeMillis()
        data.requestId = UUID.randomUUID().toString()
        return JsonOutput.toJson(data)
    } catch (Exception e) {
        return json
    }
}

def postProcess(String json, context) {
    return json
}

def configureTls(context) {
    return null
}

def getName() {
    return "添加时间戳"
}
