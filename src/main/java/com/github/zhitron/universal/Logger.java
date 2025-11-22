package com.github.zhitron.universal;

import com.github.zhitron.lambda.consumer.TripleConsumerObjectThrow;
import com.github.zhitron.lambda.predicate.SinglePredicateObjectThrow;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 通用日志处理工具类
 * <p>
 * 提供统一的日志接口，支持多种日志框架的集成，包括：
 * - 内置控制台日志（默认）
 * - Java Common Logging (JCL)
 * - Log4j
 * - Log4j2
 * - Slf4j
 * <p>
 * 日志对象{@link Logger}构造指定日志器的ID{@link #of(String)}，使用如下对象方法输出日志：
 * 如果打开了{@link #isEnabledError()}则{@link #error(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledWarn()}则{@link #warn(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledInfo()}则{@link #info(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledDebug()}则{@link #debug(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledTrace()}则{@link #trace(String, Object...)}会输出日志
 * <p>
 * 静态日志{@link Logger}，使用全局的日志器，可以通过{@link #global(String)}设置指定日志器的ID，使用如下方法输出日志：
 * 如果打开了{@link #enabledDebug()}则{@link #error(String, Object...)}会输出日志
 * 如果打开了{@link #enabledWarn()}则{@link #warn(String, Object...)}会输出日志
 * 如果打开了{@link #enabledInfo()}则{@link #info(String, Object...)}会输出日志
 * 如果打开了{@link #enabledDebug()}则{@link #debug(String, Object...)}会输出日志
 * 如果打开了{@link #enabledTrace()}则{@link #trace(String, Object...)}会输出日志
 *
 * @author zhitron
 */
@SuppressWarnings({"RedundantThrows", "unused"})
public final class Logger {

    /**
     * 日志类的完全限定类名，用于在堆栈跟踪中识别调用者
     * 通过获取Logger类的名称来初始化，避免硬编码类名
     */
    private static final String LOGGER_CLASS_NAME = Logger.class.getName();

    /**
     * 全局日志开关，使用原子布尔值保证线程安全
     * 默认值为true，表示日志功能处于开启状态
     */
    private static final AtomicBoolean LOGGER_ENABLED = new AtomicBoolean(true);

    /**
     * 线程本地变量，用于存储每个线程的日志前缀
     */
    private static final ThreadLocal<String> LOGGER_PREFIX = new ThreadLocal<>();

    /**
     * 缓存的日志工厂映射表，使用线程安全的ConcurrentSkipListMap实现
     */
    private static final Map<String, Factory> FACTORY_MAP = new ConcurrentSkipListMap<>(String::compareToIgnoreCase);

    /**
     * 控制台日志工厂实现，直接使用System.out和System.err输出日志
     */
    private static final Factory COMMON_FACTORY = new CommonFactory();

    /**
     * 默认日志实例
     */
    private static final Logger DEFAULT_LOGGER = new Logger("Console");

    /**
     * 全局日志引用，使用原子引用保证线程安全
     */
    private static final AtomicReference<Logger> GLOBAL_LOG = new AtomicReference<>(DEFAULT_LOGGER);

    /*
     * 静态初始化块，用于注册内置的日志工厂实现
     */
    static {
        // 注册内置的日志工厂实现
        FACTORY_MAP.put("Console", new CommonFactory());
        // Java Common Logging (JCL) 日志工厂实现
        FACTORY_MAP.put("jcl", new JclFactory());
        // Log4j 日志工厂实现
        FACTORY_MAP.put("log4j", new CommonFactory("Log4j", "org.apache.log4j.LogManager", "org.apache.log4j.Logger"));
        // Log4j2 日志工厂实现
        FACTORY_MAP.put("log4j2", new CommonFactory("Log4j2", "org.apache.logging.log4j.LogManager", "org.apache.logging.log4j.Logger"));
        // SLF4J 日志工厂实现
        FACTORY_MAP.put("slf4j", new CommonFactory("Slf4j", "org.slf4j.LoggerFactory", "org.slf4j.Logger"));
    }

    /**
     * 日志ID，用于标识不同的日志实现
     */
    private final String id;

    /**
     * 构造函数，创建指定ID的日志实例
     *
     * @param id 日志ID
     */
    private Logger(String id) {
        this.id = id;
    }

    /**
     * 创建指定ID的日志实例
     *
     * @param id 日志ID
     * @return {@link Logger} 日志实例
     */
    public static Logger of(String id) {
        if (id == null) {
            return DEFAULT_LOGGER;
        }
        check(id, true);
        return new Logger(id);
    }

    /**
     * 开启日志功能
     * <p>
     * 将全局日志开关设置为开启状态，允许日志输出。
     * 该方法是线程安全的。
     */
    public static void openLogger() {
        LOGGER_ENABLED.set(true);
    }

    /**
     * 关闭日志功能
     * <p>
     * 将全局日志开关设置为关闭状态，禁止所有日志输出。
     * 该方法是线程安全的。
     */
    public static void closeLogger() {
        LOGGER_ENABLED.set(false);
    }

    /**
     * 设置全局日志实现
     *
     * @param id 日志ID
     * @return 如果设置成功返回true，否则返回false
     */
    public static boolean global(String id) {
        if (check(id, false)) {
            if (!Objects.equals(global().id, id)) {
                GLOBAL_LOG.set(new Logger(id));
            }
            return true;
        }
        return false;
    }

    /**
     * 获取全局日志实例
     *
     * @return 全局日志实例
     */
    public static Logger global() {
        return GLOBAL_LOG.get();
    }

    /**
     * 添加自定义日志工厂
     *
     * @param id      日志ID
     * @param factory 日志工厂
     * @return 如果设置成功返回true，否则返回false
     */
    public static boolean addFactory(String id, Factory factory) {
        if (id != null && factory != null) {
            FACTORY_MAP.put(id, factory);
            return true;
        }
        return false;
    }

    /**
     * 检查是否启用TRACE级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledTrace() {
        return Logger.global().enabledTrace();
    }

    /**
     * 检查是否启用DEBUG级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledDebug() {
        return Logger.global().enabledDebug();
    }

    /**
     * 检查是否启用INFO级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledInfo() {
        return Logger.global().enabledInfo();
    }

    /**
     * 检查是否启用WARN级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledWarn() {
        return Logger.global().enabledWarn();
    }

    /**
     * 检查是否启用ERROR级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledError() {
        return Logger.global().enabledError();
    }

    /**
     * 输出TRACE级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void trace(String messageTemplate, Object... args) {
        Logger.global().logTrace(messageTemplate, args);
    }

    /**
     * 输出DEBUG级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void debug(String messageTemplate, Object... args) {
        Logger.global().logDebug(messageTemplate, args);
    }

    /**
     * 输出INFO级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void info(String messageTemplate, Object... args) {
        Logger.global().logInfo(messageTemplate, args);
    }

    /**
     * 输出WARN级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void warn(String messageTemplate, Object... args) {
        Logger.global().logWarn(messageTemplate, args);
    }

    /**
     * 输出ERROR级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void error(String messageTemplate, Object... args) {
        Logger.global().logError(messageTemplate, args);
    }

    /**
     * 根据条件选择抛出错误或记录TRACE日志
     *
     * @param isThrows        是否抛出错误
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T traceOrThrowError(boolean isThrows, Throwable exception, String messageTemplate, Object... args) {
        if (isThrows) {
            throw new Error(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledTrace()) {
                Logger.trace(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出异常或记录TRACE日志
     *
     * @param isThrows        是否抛出异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出异常则返回null
     * @throws Exception 当isThrows为true时抛出
     */
    public static <T> T traceOrThrowException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws Exception {
        if (isThrows) {
            throw new Exception(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledTrace()) {
                Logger.trace(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出运行时异常或记录TRACE日志
     *
     * @param isThrows        是否抛出运行时异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出运行时异常则返回null
     * @throws RuntimeException 当isThrows为true时抛出
     */
    public static <T> T traceOrThrowRuntimeException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws RuntimeException {
        if (isThrows) {
            throw new RuntimeException(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledTrace()) {
                Logger.trace(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录DEBUG日志
     *
     * @param isThrows        是否抛出错误
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T debugOrThrowError(boolean isThrows, Throwable exception, String messageTemplate, Object... args) {
        if (isThrows) {
            throw new Error(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledDebug()) {
                Logger.debug(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出异常或记录DEBUG日志
     *
     * @param isThrows        是否抛出异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出异常则返回null
     * @throws Exception 当isThrows为true时抛出
     */
    public static <T> T debugOrThrowException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws Exception {
        if (isThrows) {
            throw new Exception(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledDebug()) {
                Logger.debug(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出运行时异常或记录DEBUG日志
     *
     * @param isThrows        是否抛出运行时异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出运行时异常则返回null
     * @throws RuntimeException 当isThrows为true时抛出
     */
    public static <T> T debugOrThrowRuntimeException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws RuntimeException {
        if (isThrows) {
            throw new RuntimeException(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledDebug()) {
                Logger.debug(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录INFO日志
     *
     * @param isThrows        是否抛出错误
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T infoOrThrowError(boolean isThrows, Throwable exception, String messageTemplate, Object... args) {
        if (isThrows) {
            throw new Error(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledInfo()) {
                Logger.info(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出异常或记录INFO日志
     *
     * @param isThrows        是否抛出异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出异常则返回null
     * @throws Exception 当isThrows为true时抛出
     */
    public static <T> T infoOrThrowException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws Exception {
        if (isThrows) {
            throw new Exception(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledInfo()) {
                Logger.info(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出运行时异常或记录INFO日志
     *
     * @param isThrows        是否抛出运行时异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出运行时异常则返回null
     * @throws RuntimeException 当isThrows为true时抛出
     */
    public static <T> T infoOrThrowRuntimeException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws RuntimeException {
        if (isThrows) {
            throw new RuntimeException(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledInfo()) {
                Logger.info(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录WARN日志
     *
     * @param isThrows        是否抛出错误
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T warnOrThrowError(boolean isThrows, Throwable exception, String messageTemplate, Object... args) {
        if (isThrows) {
            throw new Error(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledWarn()) {
                Logger.warn(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出异常或记录WARN日志
     *
     * @param isThrows        是否抛出异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出异常则返回null
     * @throws Exception 当isThrows为true时抛出
     */
    public static <T> T warnOrThrowException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws Exception {
        if (isThrows) {
            throw new Exception(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledWarn()) {
                Logger.warn(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出运行时异常或记录WARN日志
     *
     * @param isThrows        是否抛出运行时异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出运行时异常则返回null
     * @throws RuntimeException 当isThrows为true时抛出
     */
    public static <T> T warnOrThrowRuntimeException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws RuntimeException {
        if (isThrows) {
            throw new RuntimeException(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledWarn()) {
                Logger.warn(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录ERROR日志
     *
     * @param isThrows        是否抛出错误
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T errorOrThrowError(boolean isThrows, Throwable exception, String messageTemplate, Object... args) {
        if (isThrows) {
            throw new Error(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledError()) {
                Logger.error(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出异常或记录ERROR日志
     *
     * @param isThrows        是否抛出异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出异常则返回null
     * @throws Exception 当isThrows为true时抛出
     */
    public static <T> T errorOrThrowException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws Exception {
        if (isThrows) {
            throw new Exception(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledError()) {
                Logger.error(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出运行时异常或记录ERROR日志
     *
     * @param isThrows        是否抛出运行时异常
     * @param exception       异常对象
     * @param messageTemplate 消息模板
     * @param args            参数列表
     * @param <T>             返回类型
     * @return 如果不抛出运行时异常则返回null
     * @throws RuntimeException 当isThrows为true时抛出
     */
    public static <T> T errorOrThrowRuntimeException(boolean isThrows, Throwable exception, String messageTemplate, Object... args) throws RuntimeException {
        if (isThrows) {
            throw new RuntimeException(String.format(messageTemplate, args), exception);
        } else {
            if (Logger.isEnabledError()) {
                Logger.error(messageTemplate, args, exception);
            }
            return null;
        }
    }

    /**
     * 检查指定ID的日志工厂是否存在且受支持
     *
     * @param id               日志ID
     * @param isThrowException 是否抛出异常
     * @return 如果存在且受支持返回true，否则返回false
     */
    private static boolean check(String id, boolean isThrowException) {
        Factory factory = FACTORY_MAP.get(id);
        if (factory == null) {
            if (!isThrowException) {
                return false;
            }
            throw new IllegalArgumentException("There is no log implementation factory for [" + id + "].");
        }
        if (factory.isSupport()) {
            return true;
        }
        if (!isThrowException) {
            return false;
        }
        throw new IllegalArgumentException("The log implementation factory that does not implement [" + id + "].");
    }

    /**
     * 获取调用日志方法的堆栈跟踪元素
     *
     * @return 调用者的堆栈跟踪元素
     */
    private static StackTraceElement getCallStackTraceElement() {
        StackTraceElement[] s = Thread.currentThread().getStackTrace();
        int i = 5;
        while (i < s.length && LOGGER_CLASS_NAME.equals(s[i].getClassName())) i++;
        return s[i];
    }

    public static void addPrefix(String prefix) {
        LOGGER_PREFIX.set(prefix);
    }

    public static void clearPrefix() {
        LOGGER_PREFIX.remove();
    }

    /**
     * 检查当前日志实例是否启用TRACE级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledTrace() {
        return FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().isEnabledFor(Level.TRACE);
    }

    /**
     * 检查当前日志实例是否启用DEBUG级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledDebug() {
        return FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().isEnabledFor(Level.DEBUG);
    }

    /**
     * 检查当前日志实例是否启用INFO级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledInfo() {
        return FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().isEnabledFor(Level.INFO);
    }

    /**
     * 检查当前日志实例是否启用WARN级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledWarn() {
        return FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().isEnabledFor(Level.WARN);
    }

    /**
     * 检查当前日志实例是否启用ERROR级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledError() {
        return FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().isEnabledFor(Level.ERROR);
    }

    /**
     * 记录TRACE级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logTrace(String messageTemplate, Object... args) {
        if (LOGGER_ENABLED.get()) {
            FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().printLog(Level.TRACE, messageTemplate, args);
        }
    }

    /**
     * 记录DEBUG级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logDebug(String messageTemplate, Object... args) {
        if (LOGGER_ENABLED.get()) {
            FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().printLog(Level.DEBUG, messageTemplate, args);
        }
    }

    /**
     * 记录INFO级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logInfo(String messageTemplate, Object... args) {
        if (LOGGER_ENABLED.get()) {
            FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().printLog(Level.INFO, messageTemplate, args);
        }
    }

    /**
     * 记录WARN级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logWarn(String messageTemplate, Object... args) {
        if (LOGGER_ENABLED.get()) {
            FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().printLog(Level.WARN, messageTemplate, args);
        }
    }

    /**
     * 记录ERROR级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logError(String messageTemplate, Object... args) {
        if (LOGGER_ENABLED.get()) {
            FACTORY_MAP.getOrDefault(id, COMMON_FACTORY).getOrCreateJournal().printLog(Level.ERROR, messageTemplate, args);
        }
    }

    /**
     * 计算当前Logger对象的哈希值
     *
     * @return 基于id属性计算的哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 判断当前Logger对象与另一个对象是否相等
     *
     * @param obj 要比较的对象
     * @return 如果对象相等返回true，否则返回false
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Logger other = (Logger) obj;
        return Objects.equals(id, other.id);
    }

    /**
     * 返回当前Logger对象的字符串表示形式
     *
     * @return Logger的id值
     */
    @Override
    public String toString() {
        return id;
    }

    /**
     * Java Common Logging (JCL) 日志工厂实现
     * 该类适配了Java内置的java.util.logging框架，将通用日志接口转换为JUL的具体调用
     */
    private static class JclFactory extends Factory {

        /**
         * 构造函数，初始化工厂名称为"Java Common Logger"
         */
        private JclFactory() {
            super("Java Common Logger");
        }

        /**
         * 创建新的日志记录器实例
         * 使用Java Util Logging的Logger.getLogger方法获取指定类名的日志记录器
         *
         * @param className 日志记录器关联的类名
         * @return java.util.logging.Logger实例
         */
        @Override
        protected Object newJournal(String className) {
            return java.util.logging.Logger.getLogger(className);
        }

        /**
         * 检查ERROR级别日志是否启用
         * 映射到java.util.logging的SEVERE级别
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         */
        @Override
        protected boolean isEnabledError(Object logger) {
            return ((java.util.logging.Logger) logger).isLoggable(java.util.logging.Level.SEVERE);
        }

        /**
         * 检查WARN级别日志是否启用
         * 映射到java.util.logging的WARNING级别
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         */
        @Override
        protected boolean isEnabledWarn(Object logger) {
            return ((java.util.logging.Logger) logger).isLoggable(java.util.logging.Level.WARNING);
        }

        /**
         * 检查INFO级别日志是否启用
         * 映射到java.util.logging的INFO级别
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         */
        @Override
        protected boolean isEnabledInfo(Object logger) {
            return ((java.util.logging.Logger) logger).isLoggable(java.util.logging.Level.INFO);
        }

        /**
         * 检查DEBUG级别日志是否启用
         * 映射到java.util.logging的CONFIG级别
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         */
        @Override
        protected boolean isEnabledDebug(Object logger) {
            return ((java.util.logging.Logger) logger).isLoggable(java.util.logging.Level.CONFIG);
        }

        /**
         * 检查TRACE级别日志是否启用
         * 映射到java.util.logging的FINE级别
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         */
        @Override
        protected boolean isEnabledTrace(Object logger) {
            return ((java.util.logging.Logger) logger).isLoggable(java.util.logging.Level.FINE);
        }

        /**
         * 输出ERROR级别日志
         * 使用logp方法记录日志，显式指定调用的类名和方法名
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素，包含类名和方法名
         * @param message           日志消息内容
         * @param throwable         异常对象，可为null
         */
        @Override
        protected void error(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) {
            ((java.util.logging.Logger) logger).logp(java.util.logging.Level.SEVERE, stackTraceElement.getClassName(), stackTraceElement.getMethodName(), message, throwable);
        }

        /**
         * 输出WARN级别日志
         * 使用logp方法记录日志，显式指定调用的类名和方法名
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素，包含类名和方法名
         * @param message           日志消息内容
         * @param throwable         异常对象，可为null
         */
        @Override
        protected void warn(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) {
            ((java.util.logging.Logger) logger).logp(java.util.logging.Level.WARNING, stackTraceElement.getClassName(), stackTraceElement.getMethodName(), message, throwable);
        }

        /**
         * 输出INFO级别日志
         * 使用logp方法记录日志，显式指定调用的类名和方法名
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素，包含类名和方法名
         * @param message           日志消息内容
         * @param throwable         异常对象，可为null
         */
        @Override
        protected void info(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) {
            ((java.util.logging.Logger) logger).logp(java.util.logging.Level.INFO, stackTraceElement.getClassName(), stackTraceElement.getMethodName(), message, throwable);
        }

        /**
         * 输出DEBUG级别日志
         * 使用logp方法记录日志，显式指定调用的类名和方法名
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素，包含类名和方法名
         * @param message           日志消息内容
         * @param throwable         异常对象，可为null
         */
        @Override
        protected void debug(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) {
            ((java.util.logging.Logger) logger).logp(java.util.logging.Level.CONFIG, stackTraceElement.getClassName(), stackTraceElement.getMethodName(), message, throwable);
        }

        /**
         * 输出TRACE级别日志
         * 使用logp方法记录日志，显式指定调用的类名和方法名
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素，包含类名和方法名
         * @param message           日志消息内容
         * @param throwable         异常对象，可为null
         */
        @Override
        protected void trace(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) {
            ((java.util.logging.Logger) logger).logp(java.util.logging.Level.FINE, stackTraceElement.getClassName(), stackTraceElement.getMethodName(), message, throwable);
        }
    }

    /**
     * 通用日志工厂实现，用于支持Log4j2和Slf4j等日志框架
     * 通过反射机制动态加载和调用目标日志框架的API
     */
    @SuppressWarnings("DuplicatedCode")
    private static class CommonFactory extends Factory {
        /**
         * 日志工厂类和日志记录器类的全限定类名
         */
        private final String loggerFactoryClassName, loggerClassName;

        /**
         * 获取日志记录器实例的方法
         */
        private Method getLogger;

        /**
         * 存储各日志级别的检查方法数组
         * 按照Level枚举的顺序存储：TRACE, DEBUG, INFO, WARN, ERROR
         */
        private SinglePredicateObjectThrow<Object, Exception> traceLevel, debugLevel, infoLevel, warnLevel, errorLevel;

        /**
         * 存储各日志级别的输出方法数组
         * 按照Level枚举的顺序存储：TRACE, DEBUG, INFO, WARN, ERROR
         */
        private TripleConsumerObjectThrow<Object, String, Throwable, Exception> traceLogger, debugLogger, infoLogger, warnLogger, errorLogger;

        /**
         * 构造函数，初始化通用日志工厂
         */
        private CommonFactory() {
            super("Console");
            this.loggerFactoryClassName = null;
            this.loggerClassName = null;
        }

        /**
         * 构造函数，初始化通用日志工厂
         *
         * @param name                   工厂名称
         * @param loggerFactoryClassName 日志工厂类的全限定类名
         * @param loggerClassName        日志记录器类的全限定类名
         */
        private CommonFactory(String name, String loggerFactoryClassName, String loggerClassName) {
            super(name);
            this.loggerFactoryClassName = loggerFactoryClassName;
            this.loggerClassName = loggerClassName;
        }

        /**
         * 初始化日志工厂，通过反射加载相关类和方法
         * 此方法会在首次使用日志工厂时被调用，用于动态加载目标日志框架的类和方法
         *
         * @return 初始化成功返回true，表示支持该日志框架
         * @throws Throwable 反射操作可能抛出的异常，如ClassNotFoundException、NoSuchMethodException等
         */
        @Override
        protected boolean init() throws Throwable {
            // 加载日志工厂类和日志记录器类
            Class<?> loggerFacotryClass = Class.forName(loggerFactoryClassName);
            Class<?> loggerClass = Class.forName(loggerClassName);

            // 获取获取日志记录器的方法
            getLogger = loggerFacotryClass.getDeclaredMethod("getLogger", String.class);
            Method traceLoggerMethod, debugLoggerMethod, infoLoggerMethod, warnLoggerMethod, errorLoggerMethod;

            // 根据工厂名称判断是否为Log4j框架
            if ("log4j".equalsIgnoreCase(name())) {
                // 加载Log4j相关的类
                Class<?> levelClass = Class.forName("org.apache.log4j.Level");
                Class<?> priorityClass = Class.forName("org.apache.log4j.Priority");
                Method isEnabledFor = loggerClass.getMethod("isEnabledFor", priorityClass);

                // 获取各个日志级别的常量对象
                // Log4j的TRACE级别在较早版本中不存在，因此需要进行异常处理
                Object traceObject;
                try {
                    traceObject = levelClass.getDeclaredField("TRACE").get(null);
                } catch (Throwable e) {
                    // 如果TRACE级别不存在，则使用DEBUG级别作为替代
                    traceObject = levelClass.getDeclaredField("DEBUG").get(null);
                }
                Object trace = traceObject;
                Object debug = levelClass.getDeclaredField("DEBUG").get(null);
                Object info = levelClass.getDeclaredField("INFO").get(null);
                Object warn = levelClass.getDeclaredField("WARN").get(null);
                Object error = levelClass.getDeclaredField("ERROR").get(null);

                // 初始化各日志级别的检查方法
                traceLevel = (logger) -> (boolean) isEnabledFor.invoke(logger, trace);
                debugLevel = (logger) -> (boolean) isEnabledFor.invoke(logger, debug);
                infoLevel = (logger) -> (boolean) isEnabledFor.invoke(logger, info);
                warnLevel = (logger) -> (boolean) isEnabledFor.invoke(logger, warn);
                errorLevel = (logger) -> (boolean) isEnabledFor.invoke(logger, error);

                // 初始化各日志级别的输出方法
                // 注意：此处两个trace方法获取代码相同，可能是为了容错处理
                try {
                    traceLoggerMethod = loggerClass.getMethod("trace", Object.class, Throwable.class);
                } catch (Throwable e) {
                    traceLoggerMethod = loggerClass.getMethod("debug", Object.class, Throwable.class);
                }
                debugLoggerMethod = loggerClass.getMethod("debug", Object.class, Throwable.class);
                infoLoggerMethod = loggerClass.getMethod("info", Object.class, Throwable.class);
                warnLoggerMethod = loggerClass.getMethod("warn", Object.class, Throwable.class);
                errorLoggerMethod = loggerClass.getMethod("error", Object.class, Throwable.class);

            } else {
                // 处理其他日志框架（如Log4j2、SLF4J等）
                // 初始化各日志级别的检查方法
                Method isTraceEnabled = loggerClass.getDeclaredMethod("isTraceEnabled");
                Method isDebugEnabled = loggerClass.getDeclaredMethod("isDebugEnabled");
                Method isInfoEnabled = loggerClass.getDeclaredMethod("isInfoEnabled");
                Method isWarnEnabled = loggerClass.getDeclaredMethod("isWarnEnabled");
                Method isErrorEnabled = loggerClass.getDeclaredMethod("isErrorEnabled");
                traceLevel = (logger) -> (boolean) isTraceEnabled.invoke(logger);
                debugLevel = (logger) -> (boolean) isDebugEnabled.invoke(logger);
                infoLevel = (logger) -> (boolean) isInfoEnabled.invoke(logger);
                warnLevel = (logger) -> (boolean) isWarnEnabled.invoke(logger);
                errorLevel = (logger) -> (boolean) isErrorEnabled.invoke(logger);

                // 初始化各日志级别的输出方法
                traceLoggerMethod = loggerClass.getMethod("trace", String.class, Throwable.class);
                debugLoggerMethod = loggerClass.getMethod("debug", String.class, Throwable.class);
                infoLoggerMethod = loggerClass.getMethod("info", String.class, Throwable.class);
                warnLoggerMethod = loggerClass.getMethod("warn", String.class, Throwable.class);
                errorLoggerMethod = loggerClass.getMethod("error", String.class, Throwable.class);
            }

            // 将方法引用赋值给对应的函数式接口
            traceLogger = traceLoggerMethod::invoke;
            debugLogger = debugLoggerMethod::invoke;
            infoLogger = infoLoggerMethod::invoke;
            warnLogger = warnLoggerMethod::invoke;
            errorLogger = errorLoggerMethod::invoke;

            return true;
        }

        /**
         * 创建指定类名的日志记录器实例
         *
         * @param className 类名
         * @return 日志记录器实例
         * @throws Throwable 反射操作可能抛出的异常
         */
        @Override
        protected Object newJournal(String className) throws Throwable {
            return getLogger.invoke(null, className);
        }

        /**
         * 检查指定日志级别是否启用
         *
         * @param logger 日志记录器实例
         * @param level  日志级别
         * @return 启用返回true，否则返回false
         * @throws Throwable 反射操作可能抛出的异常
         */
        @Override
        protected boolean isEnabledFor(Object logger, Level level) throws Throwable {
            if (isConsole()) {
                return super.isEnabledFor(logger, level);
            } else {
                switch (level) {
                    case TRACE:
                        return traceLevel.test(logger);
                    case DEBUG:
                        return debugLevel.test(logger);
                    case INFO:
                        return infoLevel.test(logger);
                    case WARN:
                        return warnLevel.test(logger);
                    case ERROR:
                        return errorLevel.test(logger);
                    default:
                        return false;
                }
            }
        }

        /**
         * 输出日志信息
         *
         * @param logger            日志记录器实例
         * @param level             日志级别
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息，可为null
         * @throws Throwable 反射操作可能抛出的异常
         */
        @Override
        protected void log(Object logger, Level level, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            if (isConsole()) {
                super.log(logger, level, stackTraceElement, message, throwable);
            } else {
                switch (level) {
                    case TRACE:
                        traceLogger.accept(logger, message, throwable);
                        break;
                    case DEBUG:
                        debugLogger.accept(logger, message, throwable);
                        break;
                    case INFO:
                        infoLogger.accept(logger, message, throwable);
                        break;
                    case WARN:
                        warnLogger.accept(logger, message, throwable);
                        break;
                    case ERROR:
                        errorLogger.accept(logger, message, throwable);
                    default:
                        super.log(logger, level, stackTraceElement, message, throwable);
                }
            }
        }
    }

    /**
     * 日志实例容器，封装了具体的日志实现对象
     * <p>
     * Journal类是日志系统的核心组件之一，负责管理特定类的日志实例。
     * 它持有工厂对象、底层日志框架的具体日志对象以及关联的类名，
     * 并提供了日志级别检查和日志输出的功能。
     */
    private static class Journal {
        /**
         * 创建此日志实例的工厂对象
         */
        private final Factory factory;

        /**
         * 底层日志框架的具体日志对象（如Log4j的Logger实例）
         */
        private final Object logger;

        /**
         * 此日志实例关联的类名
         */
        private final String className;

        /**
         * 构造一个新的日志实例容器
         *
         * @param factory   创建日志实例的工厂
         * @param logger    底层日志框架的日志对象
         * @param className 关联的类名
         */
        private Journal(Factory factory, Object logger, String className) {
            this.factory = factory;
            this.logger = logger;
            this.className = className;
        }

        /**
         * 检查指定的日志级别是否启用
         * <p>
         * 该方法首先检查全局日志开关是否打开，如果关闭则直接返回false。
         * 然后委托给对应的日志工厂检查指定级别是否启用。
         * 如果在检查过程中发生异常，则打印异常堆栈并返回false。
         *
         * @param level 要检查的日志级别
         * @return 如果启用了该级别返回true，否则返回false
         */
        private boolean isEnabledFor(Level level) {
            // 检查全局日志开关是否打开
            if (!LOGGER_ENABLED.get()) {
                return false;
            }
            try {
                // 委托给工厂检查指定级别是否启用
                return factory.isEnabledFor(logger, level);
            } catch (Throwable e) {
                // 发生异常时打印堆栈跟踪并返回false
                e.printStackTrace(System.err);
                return false;
            }
        }

        /**
         * 输出日志信息
         * <p>
         * 该方法首先检查全局日志开关是否打开，如果关闭则直接返回。
         * 然后解析调用堆栈以确定日志来源，处理参数中的异常对象，
         * 格式化日志消息，添加线程本地前缀，最后委托给工厂执行实际的日志输出。
         * 如果在处理过程中发生异常，则打印异常堆栈。
         *
         * @param level           日志级别
         * @param messageTemplate 消息模板
         * @param args            消息参数
         */
        private void printLog(Level level, String messageTemplate, Object... args) {
            // 检查全局日志开关是否打开
            if (!LOGGER_ENABLED.get()) {
                return;
            }
            // 获取调用堆栈元素以确定日志来源
            StackTraceElement callStackTraceElement = getCallStackTraceElement();

            // 处理可能存在的异常参数
            Throwable throwable = null;
            if (args != null && args.length > 0) {
                if (args[args.length - 1] instanceof Throwable) {
                    throwable = (Throwable) args[args.length - 1];
                    // 处理参数数组的情况
                    if (args.length == 2 && args[0] instanceof Object[]) {
                        args = (Object[]) args[0];
                    } else {
                        // 移除异常参数，保留其他参数
                        args = Arrays.copyOf(args, args.length - 1);
                    }
                }
            } else if (args == null) {
                args = new Object[0];
            }

            try {
                // 格式化日志消息
                String message = messageTemplate;
                if (args.length != 0) {
                    message = String.format(messageTemplate, args);
                } else if (message == null) {
                    message = "";
                }

                // 添加线程本地前缀（如果存在）
                String prefix = LOGGER_PREFIX.get();
                if (prefix != null && !prefix.isEmpty()) {
                    message = prefix + message;
                }

                // 委托给工厂执行实际的日志输出
                factory.log(logger, level, callStackTraceElement, message, throwable);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
            }
        }

        /**
         * 返回此日志实例的字符串表示形式
         *
         * @return 包含类名的日志描述字符串
         */
        @Override
        public String toString() {
            return "Logger: " + className;
        }

        /**
         * 比较此日志实例与其他对象是否相等
         *
         * @param o 要比较的对象
         * @return 如果相等返回true，否则返回false
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Journal journal = (Journal) o;
            return Objects.equals(factory, journal.factory) &&
                    Objects.equals(logger, journal.logger) &&
                    Objects.equals(className, journal.className);
        }

        /**
         * 计算此日志实例的哈希码
         *
         * @return 基于factory、logger和className计算的哈希码
         */
        @Override
        public int hashCode() {
            return Objects.hash(factory, logger, className);
        }
    }

    /**
     * 日志工厂抽象类，定义了日志工厂的基本结构和行为
     * <p>
     * Factory类是日志系统的基础设施，负责创建和管理特定日志框架的适配器。
     * 每个具体的日志实现（如Console、Log4j、Slf4j等）都需要继承此类并实现相应的方法。
     * 该类提供了日志实例的缓存机制、线程安全访问控制以及日志框架可用性检测等功能。
     */
    public static abstract class Factory {
        /**
         * 工厂名称，用于标识不同的日志实现
         */
        private final String name;

        /**
         * 日志实例缓存，使用LinkedHashMap保持插入顺序
         * 用于存储已创建的Journal对象，避免重复创建
         */
        private final Map<String, Journal> cache = new LinkedHashMap<>();

        /**
         * 读写锁，保证多线程环境下对缓存的安全访问
         * 读锁用于查询缓存，写锁用于更新缓存
         */
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        /**
         * 标识是否为控制台日志实现
         * 控制台日志具有特殊的处理逻辑
         */
        private final boolean isConsole;

        /**
         * 标识工厂是否已完成初始化
         * 使用volatile确保多线程环境下的可见性
         */
        private volatile boolean isInit = false;

        /**
         * 标识当前日志框架是否受支持
         * 通过初始化过程检测日志框架的可用性
         */
        private volatile boolean isSupport = false;

        /**
         * 默认构造函数，创建名为"console"的工厂实例
         */
        protected Factory() {
            this("Console");
        }

        /**
         * 带参构造函数，创建指定名称的工厂实例
         *
         * @param name 工厂名称
         */
        protected Factory(String name) {
            this.name = name;
            this.isConsole = "Console".equalsIgnoreCase(name);
            if (this.isConsole) {
                this.isInit = true;
                this.isSupport = true;
            }
        }

        /**
         * 获取工厂名称
         *
         * @return 工厂名称
         */
        public final String name() {
            return name;
        }

        /**
         * 检查当前日志框架是否受支持
         * 首次调用时会触发初始化过程
         *
         * @return 如果受支持返回true，否则返回false
         */
        public final boolean isSupport() {
            if (!isInit) {
                isInit = true;
                if (isConsole) {
                    isSupport = true;
                } else {
                    try {
                        isSupport = init();
                    } catch (Throwable e) {
                        isSupport = false;
                        System.err.printf("The '%s' logs are not supported due to:", name());
                        e.printStackTrace(System.err);
                    }
                }
            }
            return isSupport;
        }

        /**
         * 判断当前工厂是否为控制台日志实现
         *
         * @return 如果是控制台日志实现返回true，否则返回false
         */
        public final boolean isConsole() {
            return isConsole;
        }

        /**
         * 初始化日志工厂
         * 子类可以重写此方法以执行特定的初始化逻辑
         *
         * @return 初始化成功返回true，否则返回false
         * @throws Throwable 初始化过程中可能抛出的异常
         */
        protected boolean init() throws Throwable {
            return isConsole;
        }

        /**
         * 创建新的日志记录器实例
         * 对于控制台日志，这里简单地返回Logger类本身作为标记
         *
         * @param className 日志记录器关联的类名
         * @return Logger类对象
         * @throws Throwable 创建过程中可能抛出的异常
         */
        protected Object newJournal(String className) throws Throwable {
            return Logger.class;
        }

        /**
         * 检查指定级别的日志是否启用
         * 控制台日志默认启用所有级别的日志输出
         *
         * @param logger 日志记录器对象
         * @param level  日志级别
         * @return 总是返回true，表示所有级别都启用
         * @throws Throwable 反射操作可能抛出的异常
         */
        protected boolean isEnabledFor(Object logger, Level level) throws Throwable {
            if (isConsole || level == null) {
                return true;
            } else {
                switch (level) {
                    case TRACE:
                        return this.isEnabledTrace(logger);
                    case DEBUG:
                        return this.isEnabledDebug(logger);
                    case INFO:
                        return this.isEnabledInfo(logger);
                    case WARN:
                        return this.isEnabledWarn(logger);
                    case ERROR:
                        return this.isEnabledError(logger);
                    default:
                        return false;
                }
            }
        }

        /**
         * 检查ERROR级别日志是否启用
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         * @throws Throwable 检查过程中可能抛出的异常
         */
        protected boolean isEnabledError(Object logger) throws Throwable {
            return this.isEnabledFor(logger, Level.ERROR);
        }

        /**
         * 检查WARN级别日志是否启用
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         * @throws Throwable 检查过程中可能抛出的异常
         */
        protected boolean isEnabledWarn(Object logger) throws Throwable {
            return this.isEnabledFor(logger, Level.WARN);
        }

        /**
         * 检查INFO级别日志是否启用
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         * @throws Throwable 检查过程中可能抛出的异常
         */
        protected boolean isEnabledInfo(Object logger) throws Throwable {
            return this.isEnabledFor(logger, Level.INFO);
        }

        /**
         * 检查DEBUG级别日志是否启用
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         * @throws Throwable 检查过程中可能抛出的异常
         */
        protected boolean isEnabledDebug(Object logger) throws Throwable {
            return this.isEnabledFor(logger, Level.DEBUG);
        }

        /**
         * 检查TRACE级别日志是否启用
         *
         * @param logger 日志记录器对象
         * @return 如果启用返回true，否则返回false
         * @throws Throwable 检查过程中可能抛出的异常
         */
        protected boolean isEnabledTrace(Object logger) throws Throwable {
            return this.isEnabledFor(logger, Level.TRACE);
        }

        /**
         * 输出日志信息
         * 根据日志级别和工厂类型选择适当的输出方式
         *
         * @param logger            日志记录器对象
         * @param level             日志级别
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息
         * @throws Throwable 输出过程中可能抛出的异常
         */
        protected void log(Object logger, Level level, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            if (isConsole || level == null) {
                this.console(logger, level, stackTraceElement, message, throwable);
            } else {
                StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
                if (stackTraceElements.length >= 4 && Objects.equals(stackTraceElements[1].getMethodName(), stackTraceElements[3].getMethodName())) {
                    this.console(logger, level, stackTraceElement, message, throwable);
                } else {
                    switch (level) {
                        case TRACE:
                            this.trace(logger, stackTraceElement, message, throwable);
                            break;
                        case DEBUG:
                            this.debug(logger, stackTraceElement, message, throwable);
                            break;
                        case INFO:
                            this.info(logger, stackTraceElement, message, throwable);
                            break;
                        case WARN:
                            this.warn(logger, stackTraceElement, message, throwable);
                            break;
                        case ERROR:
                            this.error(logger, stackTraceElement, message, throwable);
                            break;
                        default:
                            this.console(logger, level, stackTraceElement, message, throwable);
                            break;
                    }
                }
            }
        }

        /**
         * 输出ERROR级别日志
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息
         * @throws Throwable 输出过程中可能抛出的异常
         */
        protected void error(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            this.log(logger, Level.ERROR, stackTraceElement, message, throwable);
        }

        /**
         * 输出WARN级别日志
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息
         * @throws Throwable 输出过程中可能抛出的异常
         */
        protected void warn(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            this.log(logger, Level.WARN, stackTraceElement, message, throwable);
        }

        /**
         * 输出INFO级别日志
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息
         * @throws Throwable 输出过程中可能抛出的异常
         */
        protected void info(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            this.log(logger, Level.INFO, stackTraceElement, message, throwable);
        }

        /**
         * 输出DEBUG级别日志
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息
         * @throws Throwable 输出过程中可能抛出的异常
         */
        protected void debug(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            this.log(logger, Level.DEBUG, stackTraceElement, message, throwable);
        }

        /**
         * 输出TRACE级别日志
         *
         * @param logger            日志记录器对象
         * @param stackTraceElement 调用堆栈元素
         * @param message           日志消息
         * @param throwable         异常信息
         * @throws Throwable 输出过程中可能抛出的异常
         */
        protected void trace(Object logger, StackTraceElement stackTraceElement, String message, Throwable throwable) throws Throwable {
            this.log(logger, Level.TRACE, stackTraceElement, message, throwable);
        }

        /**
         * 执行实际的日志输出操作
         * 格式化日志消息并根据日志级别决定输出到System.out还是System.err
         *
         * @param logger            日志记录器对象
         * @param level             日志级别
         * @param stackTraceElement 调用堆栈元素，包含类名、方法名和行号等信息
         * @param message           日志消息内容
         * @param throwable         异常对象，可为null
         */
        protected void console(Object logger, Level level, StackTraceElement stackTraceElement, String message, Throwable throwable) {
            // 格式化日志消息，包含时间戳、日志级别、调用位置和消息内容
            String s = String.format("[%s] [%s] %s: %s",
                    OffsetDateTime.now(),
                    level,
                    stackTraceElement,
                    message);

            // 根据日志级别决定输出流，ERROR级别输出到错误流，其他级别输出到标准流
            if (level == Level.ERROR) {
                System.err.println(s);
            } else {
                System.out.println(s);
            }

            // 如果有异常信息，则输出异常堆栈跟踪
            if (throwable != null) {
                if (level == Level.ERROR) {
                    throwable.printStackTrace(System.err);
                } else {
                    throwable.printStackTrace(System.out);
                }
            }
        }

        /**
         * 获取或创建日志实例
         * 首先尝试从缓存中获取，如果不存在则创建新的实例并加入缓存
         *
         * @return 日志实例
         */
        private Journal getOrCreateJournal() {
            // 检查当前日志工厂是否支持
            if (!isSupport()) {
                throw new IllegalArgumentException(String.format("The '%s' logs are not supported due to:", name()));
            }

            // 获取调用者的类名作为日志实例的标识
            String className = getCallStackTraceElement().getClassName();
            Journal journal;

            // 尝试从缓存中获取现有的日志实例（使用读锁）
            try {
                lock.readLock().lock();
                journal = cache.get(className);
            } finally {
                lock.readLock().unlock();
            }

            // 如果缓存中没有找到，则创建新的日志实例（使用写锁）
            if (journal == null) {
                try {
                    lock.writeLock().lock();
                    Throwable ex = null;
                    try {
                        // 创建新的日志对象
                        Object journalObject = createJournal(className);
                        if (journalObject != null) {
                            // 封装成Journal对象
                            journal = new Journal(this, journalObject, className);
                        }
                    } catch (Throwable e) {
                        // 记录创建过程中出现的异常
                        ex = e;
                    }

                    // 如果成功创建了日志实例，则将其放入缓存
                    if (journal != null) {
                        cache.put(className, journal);
                    } else {
                        // 如果创建失败，则抛出错误
                        throw new Error("Error implement method 'createJournal',due to the return value being null", ex);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }

            // 返回获取或创建的日志实例
            return journal;
        }

        /**
         * 根据类名创建日志对象
         *
         * @param className 类名
         * @return 日志对象
         * @throws Throwable 创建过程中可能出现的异常
         */
        private Object createJournal(String className) throws Throwable {
            if (isConsole()) {
                // 控制台日志直接返回Logger类对象
                return Logger.class;
            } else {
                // 其他日志框架调用具体的newJournal方法创建日志对象
                return newJournal(className);
            }
        }
    }

    /**
     * 日志等级枚举
     */
    public enum Level {
        /**
         * 跟踪级别 - 最详细的日志信息，通常只在开发阶段使用
         */
        TRACE,
        /**
         * 调试级别 - 用于调试应用程序的详细信息
         */
        DEBUG,
        /**
         * 信息级别 - 一般性的运行状态信息
         */
        INFO,
        /**
         * 警告级别 - 潜在的问题，但不影响程序继续运行
         */
        WARN,
        /**
         * 错误级别 - 错误事件，可能导致功能异常
         */
        ERROR
    }
}
