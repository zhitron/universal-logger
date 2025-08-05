package com.github.zhitron.universal;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 日志处理操作
 * <p>
 * 默认日志器{@link #CONSOLE}，使用的是用{@code System.out}和{@code System.err}
 * 其它内置还支持JCL,log4j,log4j2,slf4j。使用时需要引用依赖即可
 * <p>
 * 日志对象{@link Logger}构造指定日志器的ID{@link #of(String)}，使用如下对象方法输出日志：
 * 如果打开了{@link #isEnabledError()}则{@link #error(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledWarn()}则{@link #warn(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledInfo()}则{@link #info(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledDebug()}则{@link #debug(String, Object...)}会输出日志
 * 如果打开了{@link #isEnabledTrace()}则{@link #trace(String, Object...)}会输出日志
 * <p>
 * 静态日志{@link Logger}，使用全局的日志器，可以通过{@link #setGlobal(String)}设置指定日志器的ID，使用如下方法输出日志：
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
    private static final String LOG_CLASS_NAME = Logger.class.getName();
    /**
     * 缓存的日志工厂映射表，使用线程安全的ConcurrentSkipListMap实现
     */
    private static final Map<String, Factory> FACTORY_MAP = new ConcurrentSkipListMap<>(String::compareToIgnoreCase);

    /**
     * 控制台日志工厂实现，直接使用System.out和System.err输出日志
     */
    private static final Factory CONSOLE = new Factory("Console") {
        @Override
        protected Object newJournal(String className) {
            return Logger.class;
        }

        @Override
        protected boolean isEnabledFor(Object lo, Lv lv) throws Throwable {
            return true;
        }

        @Override
        protected void log(Object lo, Lv lv, StackTraceElement ste, String message, Throwable throwable) {
            String s = String.format("[%s] [%-5s] %s: %s", OffsetDateTime.now(), lv, ste, message);
            if (lv == Lv.ERROR) System.err.println(s);
            else System.out.println(s);
            if (throwable != null) {
                if (lv == Lv.ERROR) throwable.printStackTrace(System.err);
                else throwable.printStackTrace(System.out);
            }
        }
    };

    /**
     * Java Common Logging (JCL) 日志工厂实现
     */
    private static final Factory JCL = new Factory("Java Common Logger") {

        @Override
        protected Object newJournal(String className) {
            return java.util.logging.Logger.getLogger(className);
        }

        @Override
        protected boolean isEnabledError(Object lo) {
            return ((java.util.logging.Logger) lo).isLoggable(java.util.logging.Level.SEVERE);
        }

        @Override
        protected boolean isEnabledWarn(Object lo) {
            return ((java.util.logging.Logger) lo).isLoggable(java.util.logging.Level.WARNING);
        }

        @Override
        protected boolean isEnabledInfo(Object lo) {
            return ((java.util.logging.Logger) lo).isLoggable(java.util.logging.Level.INFO);
        }

        @Override
        protected boolean isEnabledDebug(Object lo) {
            return ((java.util.logging.Logger) lo).isLoggable(java.util.logging.Level.CONFIG);
        }

        @Override
        protected boolean isEnabledTrace(Object lo) {
            return ((java.util.logging.Logger) lo).isLoggable(java.util.logging.Level.FINE);
        }

        @Override
        protected void error(Object lo, StackTraceElement ste, String message, Throwable throwable) {
            ((java.util.logging.Logger) lo).logp(java.util.logging.Level.SEVERE, ste.getClassName(), ste.getMethodName(), message, throwable);
        }

        @Override
        protected void warn(Object lo, StackTraceElement ste, String message, Throwable throwable) {
            ((java.util.logging.Logger) lo).logp(java.util.logging.Level.WARNING, ste.getClassName(), ste.getMethodName(), message, throwable);
        }

        @Override
        protected void info(Object lo, StackTraceElement ste, String message, Throwable throwable) {
            ((java.util.logging.Logger) lo).logp(java.util.logging.Level.INFO, ste.getClassName(), ste.getMethodName(), message, throwable);
        }

        @Override
        protected void debug(Object lo, StackTraceElement ste, String message, Throwable throwable) {
            ((java.util.logging.Logger) lo).logp(java.util.logging.Level.CONFIG, ste.getClassName(), ste.getMethodName(), message, throwable);
        }

        @Override
        protected void trace(Object lo, StackTraceElement ste, String message, Throwable throwable) {
            ((java.util.logging.Logger) lo).logp(java.util.logging.Level.FINE, ste.getClassName(), ste.getMethodName(), message, throwable);
        }
    };

    /**
     * 默认日志工厂，当未指定日志实现时使用控制台日志
     */
    private static final Factory DEFAULT_FACTORY = CONSOLE;

    /**
     * 默认日志实例
     */
    private static final Logger DEFAULT_LOGGER = new Logger("Console");

    /**
     * 全局日志引用，使用原子引用保证线程安全
     */
    private static final AtomicReference<Logger> GLOBAL_LOG = new AtomicReference<>(DEFAULT_LOGGER);

    static {
        // 注册内置的日志工厂实现
        FACTORY_MAP.put("Console", CONSOLE);
        FACTORY_MAP.put("jcl", JCL);
        FACTORY_MAP.put("log4j", new Log4jFactory());
        FACTORY_MAP.put("log4j2", new CommonFactory("Log4j2", "org.apache.logging.log4j.LogManager", "org.apache.logging.log4j.Logger"));
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
        check(id, true);
        if (id == null) return DEFAULT_LOGGER;
        return new Logger(id);
    }

    /**
     * 设置全局日志实现
     *
     * @param id 日志ID
     * @return 如果设置成功返回true，否则返回false
     */
    public static boolean setGlobal(String id) {
        if (check(id, false)) {
            if (!Objects.equals(global().id, id)) {
                GLOBAL_LOG.set(new Logger(id));
            }
            return true;
        }
        return false;
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
        return global().enabledTrace();
    }

    /**
     * 检查是否启用DEBUG级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledDebug() {
        return global().enabledDebug();
    }

    /**
     * 检查是否启用INFO级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledInfo() {
        return global().enabledInfo();
    }

    /**
     * 检查是否启用WARN级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledWarn() {
        return global().enabledWarn();
    }

    /**
     * 检查是否启用ERROR级别日志
     *
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isEnabledError() {
        return global().enabledError();
    }

    /**
     * 输出TRACE级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void trace(String messageTemplate, Object... args) {
        global().logTrace(messageTemplate, args);
    }

    /**
     * 输出DEBUG级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void debug(String messageTemplate, Object... args) {
        global().logDebug(messageTemplate, args);
    }

    /**
     * 输出INFO级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void info(String messageTemplate, Object... args) {
        global().logInfo(messageTemplate, args);
    }

    /**
     * 输出WARN级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void warn(String messageTemplate, Object... args) {
        global().logWarn(messageTemplate, args);
    }

    /**
     * 输出ERROR级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public static void error(String messageTemplate, Object... args) {
        global().logError(messageTemplate, args);
    }

    /**
     * 根据条件选择抛出错误或记录TRACE日志
     *
     * @param isThrows  是否抛出错误
     * @param exception 异常对象
     * @param msg       消息内容
     * @param <T>       返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T traceOrThrowError(boolean isThrows, Throwable exception, String msg) {
        if (isThrows) {
            throw new Error(msg, exception);
        } else {
            if (Logger.isEnabledTrace()) {
                Logger.trace(msg, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录DEBUG日志
     *
     * @param isThrows  是否抛出错误
     * @param exception 异常对象
     * @param msg       消息内容
     * @param <T>       返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T debugOrThrowError(boolean isThrows, Throwable exception, String msg) {
        if (isThrows) {
            throw new Error(msg, exception);
        } else {
            if (Logger.isEnabledDebug()) {
                Logger.debug(msg, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录INFO日志
     *
     * @param isThrows  是否抛出错误
     * @param exception 异常对象
     * @param msg       消息内容
     * @param <T>       返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T infoOrThrowError(boolean isThrows, Throwable exception, String msg) {
        if (isThrows) {
            throw new Error(msg, exception);
        } else {
            if (Logger.isEnabledInfo()) {
                Logger.info(msg, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录WARN日志
     *
     * @param isThrows  是否抛出错误
     * @param exception 异常对象
     * @param msg       消息内容
     * @param <T>       返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T warnOrThrowError(boolean isThrows, Throwable exception, String msg) {
        if (isThrows) {
            throw new Error(msg, exception);
        } else {
            if (Logger.isEnabledWarn()) {
                Logger.warn(msg, exception);
            }
            return null;
        }
    }

    /**
     * 根据条件选择抛出错误或记录ERROR日志
     *
     * @param isThrows  是否抛出错误
     * @param exception 异常对象
     * @param msg       消息内容
     * @param <T>       返回类型
     * @return 如果不抛出错误则返回null
     */
    public static <T> T errorOrThrowError(boolean isThrows, Throwable exception, String msg) {
        if (isThrows) {
            throw new Error(msg, exception);
        } else {
            if (Logger.isEnabledError()) {
                Logger.error(msg, exception);
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
            if (!isThrowException) return false;
            throw new IllegalArgumentException("There is no log implementation factory for [" + id + "].");
        }
        if (factory.isSupport()) return true;
        if (!isThrowException) return false;
        throw new IllegalArgumentException("The log implementation factory that does not implement [" + id + "].");

    }

    /**
     * 获取全局日志实例
     *
     * @return 全局日志实例
     */
    private static Logger global() {
        return GLOBAL_LOG.get();
    }

    /**
     * 获取调用日志方法的堆栈跟踪元素
     *
     * @return 调用者的堆栈跟踪元素
     */
    private static StackTraceElement getCallStackTraceElement() {
        StackTraceElement[] s = Thread.currentThread().getStackTrace();
        int i = 5;
        while (i < s.length && LOG_CLASS_NAME.equals(s[i].getClassName())) i++;
        return s[i];
    }

    /**
     * 检查当前日志实例是否启用TRACE级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledTrace() {
        return FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().isEnabledFor(Lv.TRACE);
    }

    /**
     * 检查当前日志实例是否启用DEBUG级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledDebug() {
        return FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().isEnabledFor(Lv.DEBUG);
    }

    /**
     * 检查当前日志实例是否启用INFO级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledInfo() {
        return FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().isEnabledFor(Lv.INFO);
    }

    /**
     * 检查当前日志实例是否启用WARN级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledWarn() {
        return FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().isEnabledFor(Lv.WARN);
    }

    /**
     * 检查当前日志实例是否启用ERROR级别
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean enabledError() {
        return FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().isEnabledFor(Lv.ERROR);
    }

    /**
     * 记录TRACE级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logTrace(String messageTemplate, Object... args) {
        FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().printLog(Lv.TRACE, messageTemplate, args);
    }

    /**
     * 记录DEBUG级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logDebug(String messageTemplate, Object... args) {
        FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().printLog(Lv.DEBUG, messageTemplate, args);
    }

    /**
     * 记录INFO级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logInfo(String messageTemplate, Object... args) {
        FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().printLog(Lv.INFO, messageTemplate, args);
    }

    /**
     * 记录WARN级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logWarn(String messageTemplate, Object... args) {
        FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().printLog(Lv.WARN, messageTemplate, args);
    }

    /**
     * 记录ERROR级别日志
     *
     * @param messageTemplate 消息模板
     * @param args            参数列表
     */
    public void logError(String messageTemplate, Object... args) {
        FACTORY_MAP.getOrDefault(id, DEFAULT_FACTORY).getOrCreateJournal().printLog(Lv.ERROR, messageTemplate, args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

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

    @Override
    public String toString() {
        return id;
    }

    /**
     * 日志等级枚举
     */
    public enum Lv {
        /**
         * 跟踪级别
         */
        TRACE,
        /**
         * 调试级别
         */
        DEBUG,
        /**
         * 信息级别
         */
        INFO,
        /**
         * 警告级别
         */
        WARN,
        /**
         * 错误级别
         */
        ERROR
    }

    /**
     * 通用日志工厂实现，用于支持Log4j2和Slf4j等日志框架
     */
    @SuppressWarnings("DuplicatedCode")
    private static class CommonFactory extends Factory {
        private final String loggerFacotryClassName, loggerClassName;
        Method[] levels, methods;
        Class<?> loggerFacotryClass, loggerClass;
        Method getLogger;

        private CommonFactory(String name, String loggerFacotryClassName, String loggerClassName) {
            super(name);
            this.loggerFacotryClassName = loggerFacotryClassName;
            this.loggerClassName = loggerClassName;
        }

        @Override
        protected boolean init() throws Throwable {
            loggerFacotryClass = Class.forName(loggerFacotryClassName);
            loggerClass = Class.forName(loggerClassName);

            getLogger = loggerFacotryClass.getDeclaredMethod("getLogger", String.class);

            levels = new Method[5];
            levels[Lv.TRACE.ordinal()] = loggerClass.getDeclaredMethod("isTraceEnabled");
            levels[Lv.DEBUG.ordinal()] = loggerClass.getDeclaredMethod("isDebugEnabled");
            levels[Lv.INFO.ordinal()] = loggerClass.getDeclaredMethod("isInfoEnabled");
            levels[Lv.WARN.ordinal()] = loggerClass.getDeclaredMethod("isWarnEnabled");
            levels[Lv.ERROR.ordinal()] = loggerClass.getDeclaredMethod("isErrorEnabled");

            methods = new Method[5];
            methods[Lv.TRACE.ordinal()] = loggerClass.getMethod("trace", String.class, Throwable.class);
            methods[Lv.DEBUG.ordinal()] = loggerClass.getMethod("debug", String.class, Throwable.class);
            methods[Lv.INFO.ordinal()] = loggerClass.getMethod("info", String.class, Throwable.class);
            methods[Lv.WARN.ordinal()] = loggerClass.getMethod("warn", String.class, Throwable.class);
            methods[Lv.ERROR.ordinal()] = loggerClass.getMethod("error", String.class, Throwable.class);

            return true;
        }

        @Override
        protected Object newJournal(String className) throws Throwable {
            return getLogger.invoke(null, className);
        }

        @Override
        protected boolean isEnabledFor(Object lo, Lv lv) throws Throwable {
            return (boolean) levels[lv.ordinal()].invoke(lo);
        }

        @Override
        protected void log(Object lo, Lv lv, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            methods[lv.ordinal()].invoke(lo, message, throwable);
        }
    }

    /**
     * Log4j日志工厂实现
     */
    private static class Log4jFactory extends Factory {
        Object[] levels = new Object[5];
        Method[] methods = new Method[5];
        Class<?> loggerFacotryClass, loggerClass, levelClass, priorityClass;
        Method getLogger, isEnabledFor;

        private Log4jFactory() {
            super("Log4j");
        }

        @Override
        protected boolean init() throws Throwable {
            loggerFacotryClass = Class.forName("org.apache.log4j.Logger");
            loggerClass = Class.forName("org.apache.log4j.Logger");
            levelClass = Class.forName("org.apache.log4j.Level");
            priorityClass = Class.forName("org.apache.log4j.Priority");
            getLogger = loggerClass.getDeclaredMethod("getLogger", String.class);
            isEnabledFor = loggerClass.getMethod("isEnabledFor", priorityClass);

            levels = new Object[5];
            levels[Lv.TRACE.ordinal()] = levelClass.getDeclaredField("TRACE");
            levels[Lv.DEBUG.ordinal()] = levelClass.getDeclaredField("DEBUG");
            levels[Lv.INFO.ordinal()] = levelClass.getDeclaredField("INFO");
            levels[Lv.WARN.ordinal()] = levelClass.getDeclaredField("WARN");
            levels[Lv.ERROR.ordinal()] = levelClass.getDeclaredField("ERROR");

            methods = new Method[5];
            methods[Lv.TRACE.ordinal()] = loggerClass.getMethod("trace", Object.class, Throwable.class);
            methods[Lv.DEBUG.ordinal()] = loggerClass.getMethod("debug", Object.class, Throwable.class);
            methods[Lv.INFO.ordinal()] = loggerClass.getMethod("info", Object.class, Throwable.class);
            methods[Lv.WARN.ordinal()] = loggerClass.getMethod("warn", Object.class, Throwable.class);
            methods[Lv.ERROR.ordinal()] = loggerClass.getMethod("error", Object.class, Throwable.class);

            return true;
        }

        @Override
        protected Object newJournal(String className) throws Throwable {
            return getLogger.invoke(null, className);
        }

        @Override
        protected boolean isEnabledFor(Object lo, Lv lv) throws Throwable {
            return (boolean) isEnabledFor.invoke(lo, levels[lv.ordinal()]);
        }

        @Override
        protected void log(Object lo, Lv lv, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            methods[lv.ordinal()].invoke(lo, message, throwable);
        }
    }

    /**
     * 日志工厂抽象类，定义了日志工厂的基本结构和行为
     */
    public static abstract class Factory {
        private final String name;
        private final Map<String, Journal> cache = new HashMap<>();
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private volatile boolean isInit = false;
        private volatile boolean isSupport = false;

        protected Factory(String name) {
            this.name = name;
        }

        public final String name() {
            return name;
        }

        public final boolean isSupport() {
            if (isInit) return isSupport;
            isInit = true;
            try {
                return isSupport = init();
            } catch (Throwable e) {
                System.err.printf("The '%s' logs are not supported due to:", name());
                e.printStackTrace(System.err);
            }
            return true;
        }

        private Journal getOrCreateJournal() {
            if (!isSupport()) {
                throw new IllegalArgumentException(String.format("The '%s' logs are not supported due to:", name()));
            }
            String className = getCallStackTraceElement().getClassName();
            Journal journal;
            try {
                lock.readLock().lock();
                journal = cache.get(className);
            } finally {
                lock.readLock().unlock();
            }
            if (journal == null) {
                try {
                    lock.writeLock().lock();
                    Throwable ex = null;
                    try {
                        Object journalObject = newJournal(className);
                        if (journalObject != null) {
                            journal = new Journal(this, journalObject, className);
                        }
                    } catch (Throwable e) {
                        ex = e;
                    }
                    if (journal != null) {
                        cache.put(className, journal);
                    } else {
                        throw new Error("Error implement method 'createJournal',due to the return value being null", ex);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
            return journal;
        }

        protected boolean init() throws Throwable {
            return true;
        }

        protected abstract Object newJournal(String className) throws Throwable;

        protected boolean isEnabledFor(Object lo, Lv lv) throws Throwable {
            switch (lv) {
                case TRACE:
                    return this.isEnabledTrace(lo);
                case DEBUG:
                    return this.isEnabledDebug(lo);
                case INFO:
                    return this.isEnabledInfo(lo);
                case WARN:
                    return this.isEnabledWarn(lo);
                case ERROR:
                    return this.isEnabledError(lo);
                default:
                    return false;
            }
        }

        protected boolean isEnabledError(Object lo) throws Throwable {
            return this.isEnabledFor(lo, Lv.ERROR);
        }

        protected boolean isEnabledWarn(Object lo) throws Throwable {
            return this.isEnabledFor(lo, Lv.WARN);
        }

        protected boolean isEnabledInfo(Object lo) throws Throwable {
            return this.isEnabledFor(lo, Lv.INFO);
        }

        protected boolean isEnabledDebug(Object lo) throws Throwable {
            return this.isEnabledFor(lo, Lv.DEBUG);
        }

        protected boolean isEnabledTrace(Object lo) throws Throwable {
            return this.isEnabledFor(lo, Lv.TRACE);
        }

        protected void log(Object lo, Lv lv, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            switch (lv) {
                case TRACE:
                    this.trace(lo, ste, message, throwable);
                    break;
                case DEBUG:
                    this.debug(lo, ste, message, throwable);
                    break;
                case INFO:
                    this.info(lo, ste, message, throwable);
                    break;
                case WARN:
                    this.warn(lo, ste, message, throwable);
                    break;
                case ERROR:
                    this.error(lo, ste, message, throwable);
                    break;
                default:
                    break;
            }
        }

        protected void error(Object lo, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            this.log(lo, Lv.ERROR, ste, message, throwable);
        }

        protected void warn(Object lo, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            this.log(lo, Lv.WARN, ste, message, throwable);
        }

        protected void info(Object lo, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            this.log(lo, Lv.INFO, ste, message, throwable);
        }

        protected void debug(Object lo, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            this.log(lo, Lv.DEBUG, ste, message, throwable);
        }

        protected void trace(Object lo, StackTraceElement ste, String message, Throwable throwable) throws Throwable {
            this.log(lo, Lv.TRACE, ste, message, throwable);
        }
    }

    /**
     * 日志实例容器，封装了具体的日志实现对象
     */
    private static class Journal {
        private final Factory factory;
        private final Object logger;
        private final String className;

        private Journal(Factory factory, Object logger, String className) {
            this.factory = factory;
            this.logger = logger;
            this.className = className;
        }

        private boolean isEnabledFor(Lv lv) {
            try {
                return factory.isEnabledFor(logger, lv);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                return false;
            }
        }

        private void printLog(Lv lv, String messageTemplate, Object... args) {
            StackTraceElement ste = getCallStackTraceElement();
            Throwable throwable = null;
            if (args != null && args.length > 0) {
                if (args[args.length - 1] instanceof Throwable) {
                    throwable = (Throwable) args[args.length - 1];
                    args = Arrays.copyOf(args, args.length - 1);
                }
            } else if (args == null) {
                args = new Object[0];
            }
            try {
                String message = messageTemplate;
                if (args.length != 0) {
                    message = String.format(messageTemplate, args);
                } else if (message == null) {
                    message = "";
                }
                factory.log(logger, lv, ste, message, throwable);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
            }
        }

        @Override
        public String toString() {
            return "Logger: " + className;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Journal journal = (Journal) o;
            return Objects.equals(factory, journal.factory) &&
                    Objects.equals(logger, journal.logger) &&
                    Objects.equals(className, journal.className);
        }

        @Override
        public int hashCode() {
            return Objects.hash(factory, logger, className);
        }
    }
}
