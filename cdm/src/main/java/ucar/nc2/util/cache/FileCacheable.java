/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.util.cache;

import java.io.IOException;

/**
 * Interface for files that can be stored in FileCache.
 * Requirements:
 *   1. hashCode() must return Object.hashCode()
 *   2. close() must call cache.release(this) if cache is not null.
 *   3. must be able to detect changes in underlying object, and indicate whether it has changed.
 *
 * @author caron
 * @since Jun 2, 2008
 */
public interface FileCacheable {

  /**
   * The location of the FileCacheable. This must be sufficient for FileFactory.factory() to create the FileCacheable object
   * @return location
   */
  String getLocation();

  /**
   * Close the FileCacheable, release all resources.
   * Must call cache.release(this) if cache is not null.
   * @throws IOException on io error
   */
  void close() throws IOException;

  /**
   * Get last modified date of underlying file(s).
   * If changed since it was stored in the cache, it will be closed and recreated with FileFactory
   * @return a sequence number (typically file date), 0 if cannot change
   */
  long getLastModified();

  /**
   * If the FileCache is not null, FileCacheable.close() must call FileCache.release()
  <pre>
  public synchronized void close() throws java.io.IOException {
    if (cache != null) {
      if (cache.release(this)) return;
    }

    reallyClose();
  } </pre>
   *
   * @param fileCache must store this, use it on close as above.
   */
  void setFileCache( FileCacheIF fileCache);

  /**
   * Release any system resources like file handles.
   * Optional, implement only if you are able to reacquire.
   * Used when object is made inactive in cache.
   * @throws IOException
   */
  void release() throws IOException;

  /**
   * Reacquire any resources like file handles
   * Used when reactivating in cache.
   * @throws IOException
   */
  void reacquire() throws IOException;

}