/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.data.DSP;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.dap4lib.serial.D4DSP;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteOrder;

/**
 * Provide a DSP interface to synthetic data (see Generator.java).
 */

public class FileDSP extends D4DSP
{
    //////////////////////////////////////////////////
    // Constants

    static protected final String[] EXTENSIONS = new String[]{
            ".dap", ".raw"
    };

    //////////////////////////////////////////////////
    // Instance variables

    //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
    protected byte[] raw = null; // Complete serialized binary databuffer

    //////////////////////////////////////////////////
    // Constructor(s)

    public FileDSP()
    {
        super();
        setOrder(ByteOrder.nativeOrder());
    }

    //////////////////////////////////////////////////
    // DSP API

    /**
     * A path is file if it has no base protocol or is file:
     *
     * @param location file:/ or possibly an absolute path
     * @param context  Any parameters that may help to decide.
     * @return true if this path appears to be processible by this DSP
     */
    public boolean dspMatch(String location, DapContext context)
    {
        try {
            XURI xuri = new XURI(location);
            if(xuri.isFile()) {
                String path = xuri.getPath();
                for(String ext : EXTENSIONS) {
                    if(path.endsWith(ext))
                        return true;
                }
            }
        } catch (URISyntaxException use) {
            return false;
        }
        return false;
    }

    @Override
    public void close()
    {
    }

    @Override
    public DSP
    open(String filepath)
            throws DapException
    {
        try {
            if(filepath.startsWith("file:")) try {
                XURI xuri = new XURI(filepath);
                filepath = xuri.getPath();
            } catch (URISyntaxException use) {
                throw new DapException("Malformed filepath: " + filepath)
                        .setCode(DapCodes.SC_NOT_FOUND);
            }
            try (FileInputStream stream = new FileInputStream(filepath)) {
                this.raw = DapUtil.readbinaryfile(stream);
            }
            try (FileInputStream stream = new FileInputStream(filepath)) { // == rewind
                ChunkInputStream rdr = new ChunkInputStream(stream, RequestMode.DAP);
                String document = rdr.readDMR();
                byte[] serialdata = DapUtil.readbinaryfile(rdr);
                super.build(document, serialdata, rdr.getByteOrder());
            }
            return this;
        } catch (IOException ioe) {
            throw new DapException(ioe).setCode(DapCodes.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
