// Hook: XL Payment Confirmation Signature
// 当请求 body 的 paymentConfirmationSignature="hook" 时：
// 1. 按报文 orderTrxId 查询 XL_PAYMENT_ORDER（多条取 ORDER BY CORRELATIONSEQ, SYS_CREATION_DATE 的第一条）
// 2. 对齐 XlPaymentConfirmServiceImpl 生成 AES/ECB/PKCS5Padding 签名
// 3. 回填 paymentConfirmationSignature 后再发 REST
//
// HttpDebugger 环境变量（当前 Env）：
//   dbUrl / dbUser / dbPassword / dbType          直接 JDBC
//   或 dbHost + dbPort + dbName/dbSid/dbService   拼接 JDBC URL
//   或 dbConnectionName                           复用 SQL 客户端里保存的同名连接
//   aesKey / paymentConfirmAesEncryptKey          必选

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Base64


@Field String TRIGGER = "hook"
@Field Map DRIVER_CLASSES = [
        ORACLE    : "oracle.jdbc.OracleDriver",
        MYSQL     : "com.mysql.cj.jdbc.Driver",
        MARIADB   : "org.mariadb.jdbc.Driver",
        POSTGRESQL: "org.postgresql.Driver",
        SQLSERVER : "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        SQLITE    : "org.sqlite.JDBC",
        H2        : "org.h2.Driver"
]

@Field
Map driverCache = [:]

@Field
List driverLoaders = []

def preProcess(String json, context) {
    try {
        if (json == null || json.trim().isEmpty()) {
            return json
        }
        def data = new JsonSlurper().parseText(json)
        def holder = findHolder(data, "paymentConfirmationSignature")
        if (holder == null || !isTrigger(holder.paymentConfirmationSignature)) {
            return json
        }

        def orderTrxId = firstNonBlank(
                holder.orderTrxId,
                findValue(data, "orderTrxId"),
                findValue(data, "orderTransactionId"))
        if (!orderTrxId) {
            log(context, "ERROR", "paymentConfirmationSignature=hook 但报文缺少 orderTrxId，跳过签名")
            return json
        }

        def vars = loadEnvVars(context)
        def order = queryPaymentOrder(orderTrxId, vars, context)
        if (order == null) {
            log(context, "ERROR", "未查询到 XL_PAYMENT_ORDER, TRANSACTIONID=" + orderTrxId)
            return json
        }

        def transactionId = nvl(order.TRANSACTIONID, orderTrxId)
        def msisdn = nvl(order.MSISDN)
        def productId = nvl(order.PRODUCTID)
        def productType = nvl(order.TRANSACTIONTYPE)
        def paymentType = nvl(order.PAYMENTTYPE)
        def eventId = nvl(order.ESBUUID)

        if (productType.length() < 7) {
            log(context, "ERROR", "TRANSACTIONTYPE 长度不足 7，无法按业务规则 substring(0,7): " + productType)
            return json
        }

        def salt = transactionId.concat(productType.substring(0, 7))
        def signaturePlainText = salt + transactionId + msisdn + productId + productType + paymentType + salt
        def aesKey = resolveAesKey(vars)
        def signature = encrypt256(signaturePlainText, aesKey)

        holder.paymentConfirmationSignature = signature
        if (isTrigger(holder.eventId) && eventId) {
            holder.eventId = eventId
        }

        log(context, "INFO", "orderTrxId=" + transactionId
                + " msisdn=" + msisdn
                + " productId=" + productId
                + " productType=" + productType
                + " paymentType=" + paymentType)
        log(context, "INFO", "签名明文: " + signaturePlainText)
        log(context, "INFO", "paymentConfirmationSignature: " + signature)
        return JsonOutput.toJson(data)
    } catch (Exception e) {
        log(context, "ERROR", "XL 支付确认签名失败: " + e.message)
        e.printStackTrace()
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
    return "XL支付确认签名"
}

String opencglHome() {
    return System.getProperty("user.home") + File.separator + ".opencgl"
}

String opencglDbFile() {
    return opencglHome() + File.separator + "conf" + File.separator + "dbs" + File.separator + "data.db"
}

boolean isTrigger(value) {
    return value != null && TRIGGER.equalsIgnoreCase(value.toString().trim())
}

def findHolder(node, String key) {
    if (node instanceof Map) {
        if (node.containsKey(key)) {
            return node
        }
        for (def child : node.values()) {
            def found = findHolder(child, key)
            if (found != null) {
                return found
            }
        }
    } else if (node instanceof List) {
        for (def child : node) {
            def found = findHolder(child, key)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

def findValue(node, String key) {
    if (node instanceof Map) {
        if (node.containsKey(key) && node[key] != null) {
            return node[key].toString()
        }
        for (def child : node.values()) {
            def found = findValue(child, key)
            if (found) {
                return found
            }
        }
    } else if (node instanceof List) {
        for (def child : node) {
            def found = findValue(child, key)
            if (found) {
                return found
            }
        }
    }
    return null
}

Map loadEnvVars(context) {
    def vars = [:]
    def extra = context?.get("envVars")
    if (extra instanceof Map) {
        extra.each { k, v -> if (k != null && v != null) vars[k.toString()] = v.toString() }
    }
    def envService = context?.get("env")
    if (envService != null && vars.isEmpty()) {
        try {
            def fromEnv = envService.getEnvironment(context.getEnvironment())
            if (fromEnv instanceof Map) {
                fromEnv.each { k, v -> if (k != null && v != null) vars[k.toString()] = v.toString() }
            }
        } catch (Exception ignored) {
        }
    }
    return vars
}

String resolveAesKey(Map vars) {
    return firstNonBlank(
            vars.aesKey,
            vars.paymentConfirmAesEncryptKey,
            vars.AES_ENCRYPT_KEY,
            vars.AES_KEY)
}

String firstNonBlank(Object... values) {
    for (def value : values) {
        if (value != null && !value.toString().trim().isEmpty()) {
            return value.toString().trim()
        }
    }
    return null
}

String nvl(Object value, String fallback = "") {
    return value == null ? fallback : value.toString()
}

Map queryPaymentOrder(String orderTrxId, Map vars, context) {
    Connection conn = null
    PreparedStatement ps = null
    ResultSet rs = null
    try {
        conn = openConnection(vars, context)
        def sql = """
            SELECT TRANSACTIONID, MSISDN, PRODUCTID, TRANSACTIONTYPE, PAYMENTTYPE, ESBUUID
            FROM XL_PAYMENT_ORDER
            WHERE TRANSACTIONID = ?
            ORDER BY CORRELATIONSEQ ASC, SYS_CREATION_DATE ASC
        """
        ps = conn.prepareStatement(sql)
        ps.setString(1, orderTrxId)
        rs = ps.executeQuery()
        if (!rs.next()) {
            return null
        }
        return [
                TRANSACTIONID  : rs.getString("TRANSACTIONID"),
                MSISDN         : rs.getString("MSISDN"),
                PRODUCTID      : rs.getString("PRODUCTID"),
                TRANSACTIONTYPE: rs.getString("TRANSACTIONTYPE"),
                PAYMENTTYPE    : rs.getString("PAYMENTTYPE"),
                ESBUUID        : rs.getString("ESBUUID")
        ]
    } finally {
        closeQuietly(rs)
        closeQuietly(ps)
        closeQuietly(conn)
    }
}

Connection openConnection(Map vars, context) {
    def connCfg = resolveConnection(vars, context)
    def url = connCfg.url
    def user = connCfg.user
    def password = connCfg.password
    def dbType = connCfg.dbType
    if (!url) {
        throw new IllegalStateException("未配置数据库连接：请在当前 Env 中设置 dbUrl（或 dbHost/dbName），或 dbConnectionName")
    }
    log(context, "INFO", "连接数据库: " + url + " type=" + dbType)
    def driver = loadDriver(dbType, url)
    def props = new Properties()
    if (user) {
        props.setProperty("user", user)
    }
    if (password != null) {
        props.setProperty("password", password)
    }
    if (driver != null) {
        def conn = driver.connect(url, props)
        if (conn != null) {
            return conn
        }
    }
    return user ? DriverManager.getConnection(url, user, password) : DriverManager.getConnection(url)
}

Map resolveConnection(Map vars, context) {
    def url = firstNonBlank(vars.dbUrl, vars.jdbcUrl, vars.DB_URL)
    def user = firstNonBlank(vars.dbUser, vars.dbUsername, vars.username, vars.DB_USER)
    def password = firstNonBlank(vars.dbPassword, vars.password, vars.DB_PASSWORD)
    def dbType = normalizeDbType(firstNonBlank(vars.dbType, vars.DB_TYPE), url)
    if (url) {
        return [url: url, user: user, password: password, dbType: dbType]
    }

    def saved = loadSavedConnection(firstNonBlank(vars.dbConnectionName, vars.dbName), context)
    if (saved != null) {
        return saved
    }

    def host = firstNonBlank(vars.dbHost, vars.host)
    def port = firstNonBlank(vars.dbPort, vars.port)
    def database = firstNonBlank(vars.dbSid, vars.dbService, vars.database, vars.dbSchema)
    dbType = normalizeDbType(firstNonBlank(vars.dbType, vars.DB_TYPE), null)
    if (host && database) {
        return [
                url     : buildJdbcUrl(dbType, host, port, database),
                user    : user,
                password: password,
                dbType  : dbType
        ]
    }
    return [url: null, user: user, password: password, dbType: dbType]
}

Map loadSavedConnection(String connectionName, context) {
    if (!connectionName) {
        return null
    }
    Connection conn = null
    PreparedStatement ps = null
    ResultSet rs = null
    try {
        Class.forName("org.sqlite.JDBC")
        def sqliteUrl = "jdbc:sqlite:" + opencglDbFile()
        conn = DriverManager.getConnection(sqliteUrl)
        ps = conn.prepareStatement("""
            SELECT host, port, database_name, username, password, db_type, name
            FROM sql_tree_item
            WHERE node_type = 'CONNECTION' AND name = ?
            LIMIT 1
        """)
        ps.setString(1, connectionName)
        rs = ps.executeQuery()
        if (!rs.next()) {
            log(context, "WARN", "未找到 SQL 客户端连接: " + connectionName)
            return null
        }
        def dbType = normalizeDbType(rs.getString("db_type"), null)
        def host = rs.getString("host")
        def port = rs.getObject("port")?.toString()
        def database = rs.getString("database_name")
        def user = rs.getString("username")
        def password = decryptSqlClientPassword(rs.getString("password"))
        log(context, "INFO", "复用 SQL 客户端连接: " + connectionName)
        return [
                url     : buildJdbcUrl(dbType, host, port, database),
                user    : user,
                password: password,
                dbType  : dbType
        ]
    } catch (Exception e) {
        log(context, "WARN", "读取 SQL 客户端连接失败: " + e.message)
        return null
    } finally {
        closeQuietly(rs)
        closeQuietly(ps)
        closeQuietly(conn)
    }
}

String normalizeDbType(String dbType, String url) {
    def raw = (dbType ?: "").trim().toUpperCase().replace("-", "").replace(" ", "")
    if (raw.contains("ORACLE") || raw == "ORA") return "ORACLE"
    if (raw.contains("MARIA")) return "MARIADB"
    if (raw.contains("MYSQL")) return "MYSQL"
    if (raw.contains("POSTGRE") || raw == "PG") return "POSTGRESQL"
    if (raw.contains("SQLSERVER") || raw.contains("MSSQL")) return "SQLSERVER"
    if (raw.contains("SQLITE")) return "SQLITE"
    if (raw.contains("H2")) return "H2"
    def u = (url ?: "").toLowerCase()
    if (u.contains("oracle")) return "ORACLE"
    if (u.contains("mariadb")) return "MARIADB"
    if (u.contains("mysql")) return "MYSQL"
    if (u.contains("postgres")) return "POSTGRESQL"
    if (u.contains("sqlserver")) return "SQLSERVER"
    if (u.contains("sqlite")) return "SQLITE"
    if (u.contains("h2:")) return "H2"
    return "ORACLE"
}

String buildJdbcUrl(String dbType, String host, String port, String database) {
    def type = normalizeDbType(dbType, null)
    def p = firstNonBlank(port, defaultPort(type))
    switch (type) {
        case "MYSQL":
            return "jdbc:mysql://${host}:${p}/${database}?useSSL=false&serverTimezone=UTC"
        case "MARIADB":
            return "jdbc:mariadb://${host}:${p}/${database}?useSsl=false"
        case "POSTGRESQL":
            return "jdbc:postgresql://${host}:${p}/${database}"
        case "SQLSERVER":
            return "jdbc:sqlserver://${host}:${p};databaseName=${database};encrypt=false;trustServerCertificate=true"
        case "SQLITE":
            return "jdbc:sqlite:${database ?: host}"
        case "H2":
            return "jdbc:h2:${database ?: host}"
        default:
            if (database && database.startsWith("/")) {
                return "jdbc:oracle:thin:@//${host}:${p}${database}"
            }
            return "jdbc:oracle:thin:@${host}:${p}:${database}"
    }
}

String defaultPort(String dbType) {
    switch (dbType) {
        case "MYSQL":
        case "MARIADB": return "3306"
        case "POSTGRESQL": return "5432"
        case "SQLSERVER": return "1433"
        case "H2": return "9092"
        default: return "1521"
    }
}

Driver loadDriver(String dbType, String url) {
    def className = DRIVER_CLASSES[normalizeDbType(dbType, url)]
    if (!className) {
        return null
    }
    if (driverCache.containsKey(className)) {
        return (Driver) driverCache[className]
    }
    def loaders = collectClassLoaders()
    for (def loader : loaders) {
        try {
            def driver = (Driver) Class.forName(className, true, loader).getDeclaredConstructor().newInstance()
            driverCache[className] = driver
            return driver
        } catch (Throwable ignored) {
        }
    }
    for (def jar : findPluginJars()) {
        try {
            def loader = new URLClassLoader([jar.toURI().toURL()] as URL[], getClass().classLoader)
            def driver = (Driver) Class.forName(className, true, loader).getDeclaredConstructor().newInstance()
            driverLoaders << loader
            driverCache[className] = driver
            return driver
        } catch (Throwable ignored) {
        }
    }
    return null
}

List collectClassLoaders() {
    def loaders = []
    def seen = new HashSet()
    def add = { ClassLoader loader ->
        while (loader != null && seen.add(loader)) {
            loaders << loader
            loader = loader.getParent()
        }
    }
    add(Thread.currentThread().contextClassLoader)
    add(getClass().classLoader)
    add(ClassLoader.getSystemClassLoader())
    return loaders
}

List findPluginJars() {
    def files = []
    def dirs = [
            new File(opencglHome(), "ext-plugin"),
            new File(System.getProperty("user.dir"), "bin"),
            new File("bin")
    ]
    dirs.each { dir ->
        if (dir.isDirectory()) {
            def jars = dir.listFiles({ File f -> f.isFile() && f.name.toLowerCase().endsWith(".jar") } as FileFilter)
            if (jars != null) {
                files.addAll(jars)
            }
        }
    }
    return files
}

String encrypt256(String plainText, String secret) {
    byte[] keyBytes = initByteArray(secret)
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"))
    byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8))
    return Base64.getEncoder().encodeToString(encrypted)
}

byte[] initByteArray(String secret) {
    byte[] keyBytes = new byte[32]
    int i = 0
    int secretLength = secret == null ? 0 : secret.length()
    while (i < secretLength && i < 32) {
        keyBytes[i] = (byte) secret.charAt(i)
        i++
    }
    while (i < 32) {
        keyBytes[i] = (byte) i
        i++
    }
    return keyBytes
}

String decryptSqlClientPassword(String encryptedText) {
    if (encryptedText == null || encryptedText.isEmpty() || encryptedText.length() < 50) {
        return encryptedText
    }
    try {
        byte[] key = new byte[32]
        byte[] secretBytes = "OpenCGL2024SecretKeyForEncryption".getBytes(StandardCharsets.UTF_8)
        System.arraycopy(secretBytes, 0, key, 0, Math.min(secretBytes.length, key.length))
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"))
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)), StandardCharsets.UTF_8)
    } catch (Exception ignored) {
        return encryptedText
    }
}

void closeQuietly(Object closeable) {
    try {
        closeable?.close()
    } catch (Exception ignored) {
    }
}

void log(context, String level, String message) {
    def line = "[" + level + "] " + message
    try {
        def scriptLog = context?.getScriptLog()
        if (scriptLog != null) {
            if ("ERROR".equals(level)) {
                scriptLog.error(message)
            } else if ("WARN".equals(level)) {
                scriptLog.warn(message)
            } else {
                scriptLog.info(message)
            }
            return
        }
    } catch (Exception ignored) {
    }
    context?.println(line)
}
