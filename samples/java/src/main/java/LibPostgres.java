import java.io.File;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;

public class LibPostgres{


    public static API api = null;
    public interface API extends Library {
        public int postgresstorage_init();
    }

}