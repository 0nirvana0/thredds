/*
 * Copyright (c) 1998 - 2011. University Corporation for Atmospheric Research/Unidata
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.grib;

import thredds.inventory.CollectionManager;
import ucar.nc2.grib.grib1.Grib1CollectionBuilder;
//import ucar.nc2.grib.grib2.Grib2Index;
import ucar.nc2.grib.grib2.Grib2CollectionBuilder;
import ucar.unidata.io.RandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.util.Formatter;

/**
 * Abstraction for Grib1Index and Grib2Index so GribCollection can handle both.
 *
 * @author John
 * @since 9/5/11
 */
public abstract class GribIndex {
  public static final String IDX_EXT = ".gbx9";
  public static final boolean debug = false;

  public GribCollection makeCollection(RandomAccessFile raf, CollectionManager.Force force, Formatter f, int edition) throws IOException {
    boolean write = false, rewrite = false;

    String filename = raf.getLocation();
    File dataFile = new File(filename);
    boolean readOk;
    try {
      readOk = readIndex(filename, dataFile.lastModified(), force); // heres where the index date is checked against the data file
    } catch (IOException ioe) {
      readOk = false;
    }
    // make or remake the index
    if (!readOk) {
      makeIndex(filename, f);
      f.format("  Index written: %s%n", filename + IDX_EXT);
    } else if (debug) {
      f.format("  Index read: %s%n", filename + IDX_EXT);
    }

    if (edition == 1)
      return Grib1CollectionBuilder.createFromSingleFile(dataFile, f);
    else
      return Grib2CollectionBuilder.createFromSingleFile(dataFile, f);
  }

  public abstract boolean readIndex(String location, long dataModified, CollectionManager.Force force) throws IOException;

  public abstract boolean makeIndex(String location, Formatter f) throws IOException;


}
