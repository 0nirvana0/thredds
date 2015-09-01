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
package ucar.nc2.ft2.coverage;

import net.jcip.annotations.Immutable;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.util.Indent;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Formatter;
import java.util.List;

/**
 * Coverage - aka Grid or GeoGrid.
 *
 * @author caron
 * @since 7/11/2015
 */
@Immutable
public class Coverage implements IsMissingEvaluator {
  private final String name;
  private final DataType dataType;
  private final List<Attribute> atts;
  private final String units, description;
  private final String coordSysName;
  protected final CoverageReader reader;
  protected final Object user;

  private CoverageCoordSys coordSys; // almost immutable

  public Coverage(String name, DataType dataType, List<Attribute> atts, String coordSysName, String units, String description, CoverageReader reader, Object user) {
    this.name = name;
    this.dataType = dataType;
    this.atts = atts;
    this.coordSysName = coordSysName;
    this.units = units;
    this.description = description;
    this.reader = reader;
    this.user = user;
  }

  // copy constructor
  public Coverage(Coverage from, Object user) {
    this.name = from.getName();
    this.dataType = from.getDataType();
    this.atts = from.getAttributes();
    this.units = from.getUnits();
    this.description = from.getDescription();
    this.coordSysName = from.getCoordSysName();
    this.reader = from.reader;
    this.user = (user == null) ? from.user : user;
  }

  void setCoordSys (CoverageCoordSys coordSys) {
    if (this.coordSys != null) throw new RuntimeException("Cant change coordSys once set");
    this.coordSys = coordSys;
  }

  public String getName() {
    return name;
  }

  public DataType getDataType() {
    return dataType;
  }

  public List<Attribute> getAttributes() {
    return atts;
  }

  public String getCoordSysName() {
    return coordSysName;
  }

  public String getUnits() {
    return units;
  }

  public String getDescription() {
    return description;
  }

  public Object getUserObject() {
    return user;
  }

  @Override
  public String toString() {
    Formatter f = new Formatter();
    Indent indent = new Indent(2);
    toString(f, indent);
    return f.toString();
  }

  public void toString(Formatter f, Indent indent) {
    indent.incr();
    f.format("%n%s  %s %s(%s) desc='%s' units='%s'%n", indent, dataType, name, coordSysName, description, units);
    f.format("%s    attributes:%n", indent);
    for (Attribute att : atts)
      f.format("%s     %s%n", indent, att);
    indent.decr();
  }

  @Nonnull
  public CoverageCoordSys getCoordSys() {
    return coordSys;
  }

  ///////////////////////////////////////////////////////////////

  public long getSizeInBytes() {
    long total = 1;
    for (String axisName : coordSys.getAxisNames()) {  // LOOK this assumes a certain order
      CoverageCoordAxis axis = coordSys.getAxis(axisName);
      total *= axis.getNcoords();
    }
    total *= getDataType().getSize();
    return total;
  }

  // LOOK must conform to whatever grid.readData() returns
  // LOOK need to deal with runtime(time), runtime(runtime, time)
  public String getIndependentAxisNamesOrdered() {
    StringBuilder sb = new StringBuilder();
    for (CoverageCoordAxis axis : coordSys.getAxes()) {
      if (!(axis.getDependenceType() == CoverageCoordAxis.DependenceType.independent)) continue;
      sb.append(axis.getName());
      sb.append(" ");
    }
    return sb.toString();
  }

  @Override
  public boolean hasMissing() {
    return true;
  }

  @Override
  public boolean isMissing(double val) {
    return Double.isNaN(val);
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////

  public GeoReferencedArray readData(SubsetParams subset) throws IOException, InvalidRangeException {
    return reader.readData(this, subset, false);
  }
}
