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
 *
 */
package ucar.nc2.ft2.coverage;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.AttributeContainerHelper;
import ucar.nc2.NCdumpW;
import ucar.nc2.constants.AxisType;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.util.Indent;
import ucar.nc2.util.Misc;
import ucar.nc2.util.Optional;

import javax.annotation.Nonnull;
import java.util.Formatter;

/**
 * FmrcTimeAxis2D: time(runtime, time)
 * LOOK not up to date
 * @author caron
 * @since 7/15/2015
 */
public class FmrcTimeAxis2D extends CoverageCoordAxis {

  // can only be set once, needed for subsetting
  private int[] shape;
  private CoverageCoordAxis1D runCoord;

  public FmrcTimeAxis2D( CoverageCoordAxisBuilder builder) {
    super( builder);
  }

  @Override
  protected void setDataset(CoordSysContainer dataset) {
    if (shape != null) throw new RuntimeException("Cant change axis once set");
    shape = new int[2];
    String axisName = dependsOn.get(0);
    CoverageCoordAxis axis = dataset.findCoordAxis(axisName);
    shape[0] = axis.getNcoords();
    shape[1] = ncoords / shape[0];

    assert axis instanceof CoverageCoordAxis1D;
    assert axis.getAxisType() == AxisType.RunTime;
    runCoord = (CoverageCoordAxis1D) axis;
  }

  @Override
  public CoverageCoordAxis copy() {
    return new FmrcTimeAxis2D(new CoverageCoordAxisBuilder(this));
  }

  @Override
  public int[] getShape() {
    return shape;
  }

  @Override
  public void toString(Formatter f, Indent indent) {
    super.toString(f, indent);
    f.format("%s  %s%n", indent, Misc.showInts(shape));
    Array data = getCoordsAsArray();
    f.format("%s%n", NCdumpW.toString(data, getName()+" values", null));
  }

  @Override
  public Optional<CoverageCoordAxis> subset(SubsetParams params) {
    if (params == null)
      return Optional.of(new FmrcTimeAxis2D( new CoverageCoordAxisBuilder(this)));

    CoordAxisHelper helper = new CoordAxisHelper(runCoord);
    int run_index = -1;

    if (params.isTrue(SubsetParams.runtimeLatest))
      run_index = runCoord.getNcoords()-1;
    else {
      CalendarDate rundate = (CalendarDate) params.get(SubsetParams.runtime);
      if (rundate != null) {
        double rundateTarget = runCoord.convert(rundate);
        run_index = helper.findCoordElement(rundateTarget, true);  // LOOK Bounded
      }
    }

    if (run_index >= 0) {
      CoverageCoordAxis1D time1D = getTimeAxisForRun(run_index);
      return time1D.subset(params);
    }

    return Optional.of(new FmrcTimeAxis2D( new CoverageCoordAxisBuilder(this)));
  }

  @Override
  public Optional<CoverageCoordAxis> subset(double minValue, double maxValue, int stride) { // LOOK not implemented, maybe illegal ??
    return Optional.of(new FmrcTimeAxis2D( new CoverageCoordAxisBuilder(this)));
  }

  @Override
  @Nonnull
  public Optional<CoverageCoordAxis> subsetDependent(CoverageCoordAxis1D from) { // LOOK not implemented, maybe illegal ??
    return null; // LOOK
  }

  @Override
  public Array getCoordsAsArray() {
    double[] values = getValues();
    return Array.factory(DataType.DOUBLE, shape, values);
  }

  @Override
  public Array getCoordBoundsAsArray() {
    double[] values = getValues();
    int[] shapeB = new int[3];
    System.arraycopy(shape, 0, shapeB, 0, 2);
    shapeB[2] = 2;
    return Array.factory(DataType.DOUBLE, shapeB, values);
  }

  public CoverageCoordAxis1D getTimeAxisForRun(CalendarDate rundate) {
    double rundateTarget = runCoord.convert(rundate);
    int run_index = new CoordAxisHelper(runCoord).findCoordElement(rundateTarget, false);  // LOOK not Bounded
    return (run_index < 0 || run_index >= runCoord.getNcoords()) ? null : getTimeAxisForRun(run_index);
  }

  public CoverageCoordAxis1D getTimeAxisForRun(int run_index) {
    if (spacing == Spacing.irregularPoint) {
      Array data = getCoordsAsArray();
      Array subset = data.slice(0, run_index);

      int count = 0;
      int n = (int) subset.getSize();
      double[] values = new double[n];
      while (subset.hasNext())
        values[count++] = subset.nextDouble();

      return new CoverageCoordAxis1D(
              new CoverageCoordAxisBuilder(name, units, description, dataType, axisType,
              AttributeContainerHelper.filter(attributes, "_Coordinate"),
              dependenceType, getDependsOn(), spacing, n, values[0], values[n - 1],
              0.0, values, reader, true));
    }

    if (spacing == Spacing.discontiguousInterval) {
      Array data = getCoordBoundsAsArray();
      Array subset = data.slice(0, run_index);

      int count = 0;
      int n = (int) subset.getSize();
      double[] values = new double[n];
      while (subset.hasNext())
        values[count++] = subset.nextDouble();

      return new CoverageCoordAxis1D(
              new CoverageCoordAxisBuilder(name, units, description, dataType, axisType,
              AttributeContainerHelper.filter(attributes, "_Coordinate"),
              dependenceType, getDependsOn(), spacing, n/2, values[0], values[n - 1],
              0.0, values, reader, true));
    }

    return null;
  }


}
