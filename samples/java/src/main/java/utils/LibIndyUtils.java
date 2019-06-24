package utils;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import org.hyperledger.indy.sdk.LibIndy;

import java.io.File;

public abstract  class LibIndyUtils {
    public static final String LIBRARY_NAME = "indy";

    /*
     * Native library interface
     */

    /**
     * JNA method signatures for calling SDK function.
     */
    public interface API extends Library {


        public int indy_issuer_recover_credential(int command_handle, int wallet_handle, int blob_storage_reader_handle, String rev_reg_id, String cred_revoc_id, Callback cb);
        int indy_set_logger(Pointer context, Callback enabled, Callback log, Callback flush);

        int indy_set_runtime_config(String config);
        int indy_get_current_error(PointerByReference error);

    }

    /*
     * Initialization
     */

    public static LibIndyUtils.API api = null;

    static {

        try {

            init();
        } catch (UnsatisfiedLinkError ex) {

            // Library could not be found in standard OS locations.
            // Call init(File file) explicitly with absolute library path.
        }
    }

    /**
     * Initializes the API with the path to the C-Callable library.
     *
     * @param searchPath The path to the directory containing the C-Callable library file.
     */
    public static void init(String searchPath) {

        NativeLibrary.addSearchPath(LIBRARY_NAME, searchPath);
        api = Native.loadLibrary(LIBRARY_NAME, LibIndyUtils.API.class);
        initLogger();
    }

    /**
     * Initializes the API with the path to the C-Callable library.
     * Warning: This is not platform-independent.
     *
     * @param file The absolute path to the C-Callable library file.
     */
    public static void init(File file) {

        api = Native.loadLibrary(file.getAbsolutePath(), LibIndyUtils.API.class);
        initLogger();
    }

    /**
     * Initializes the API with the default library.
     */
    public static void init() {

        api = Native.loadLibrary(LIBRARY_NAME, LibIndyUtils.API.class);
        initLogger();
    }

    /**
     * Indicates whether or not the API has been initialized.
     *
     * @return true if the API is initialize, otherwise false.
     */
    public static boolean isInitialized() {

        return api != null;
    }

    private static class Logger {
        private static Callback enabled = null;

        private static Callback log = new Callback() {

            @SuppressWarnings({"unused", "unchecked"})
            public void callback(Pointer context, int level, String target, String message, String module_path, String file, int line) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(String.format("%s.native.%s", LibIndyUtils.class.getName(), target.replace("::", ".")));

                String logMessage = String.format("%s:%d | %s", file, line, message);

                switch (level) {
                    case 1:
                        logger.error(logMessage);
                        break;
                    case 2:
                        logger.warn(logMessage);
                        break;
                    case 3:
                        logger.info(logMessage);
                        break;
                    case 4:
                        logger.debug(logMessage);
                        break;
                    case 5:
                        logger.trace(logMessage);
                        break;
                    default:
                        break;
                }
            }
        };

        private static Callback flush = null;
    }

    private static void initLogger() {
        api.indy_set_logger(null, LibIndyUtils.Logger.enabled, LibIndyUtils.Logger.log, LibIndyUtils.Logger.flush);
    }

    /**
     * Set libindy runtime configuration. Can be optionally called to change current params.
     *
     * @param config config: {
     *     "crypto_thread_pool_size": Optional[int] - size of thread pool for the most expensive crypto operations. (4 by default)
     *     "collect_backtrace": Optional[bool] - whether errors backtrace should be collected.
     *         Capturing of backtrace can affect library performance.
     *         NOTE: must be set before invocation of any other API functions.
     *  }
     */
    public static void setRuntimeConfig(String config) {
        api.indy_set_runtime_config(config);
    }
}
