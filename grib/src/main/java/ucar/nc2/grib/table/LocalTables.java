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

package ucar.nc2.grib.table;

import java.util.*;

/**
 * superlass for local table implementations
 *
 * @author John
 * @since 6/22/11
 */
public abstract class LocalTables extends GribTables {
  protected final Map<Integer, TableEntry> local = new HashMap<Integer, TableEntry>(100);

  LocalTables(int center, int subCenter, int masterVersion, int localVersion) {
    super(center, subCenter, masterVersion, localVersion);
    initLocalTable();
  }

  protected abstract void initLocalTable();


  @Override
  public List getParameters() {
    List<TableEntry> result = new ArrayList<TableEntry>();
    for (TableEntry p : local.values()) result.add(p);
    Collections.sort(result);
    return result;
  }

  @Override
  public String getVariableName(int discipline, int category, int parameter) {
    if ((category <= 191) && (parameter <= 191))
      return super.getVariableName(discipline, category, parameter);

    GribTables.Parameter te = getParameter(discipline, category, parameter);
    if (te == null)
      return super.getVariableName(discipline, category, parameter);
    else
      return te.getName();
  }

  @Override
  public GribTables.Parameter getParameter(int discipline, int category, int number) {
    if ((category <= 191) && (number <= 191))
      return WmoCodeTable.getParameterEntry(discipline, category, number);
    return local.get(makeHash(discipline, category, number));
  }

}
